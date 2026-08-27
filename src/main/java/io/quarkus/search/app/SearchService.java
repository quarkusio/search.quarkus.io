package io.quarkus.search.app;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.search.app.dto.GroupedGuideHit;
import io.quarkus.search.app.dto.GroupedSearchResult;
import io.quarkus.search.app.dto.GroupedSearchResult.CategoryGroup;
import io.quarkus.search.app.dto.GuideSearchHit;
import io.quarkus.search.app.dto.SearchResult;
import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.entity.QuarkusVersionAndLanguageRoutingBinder;
import io.quarkus.search.app.quarkiverseio.QuarkiverseIO;
import io.quarkus.search.app.quarkusio.QuarkusIO;

import io.quarkus.logging.Log;

import org.hibernate.search.backend.elasticsearch.ElasticsearchExtension;
import org.hibernate.search.backend.elasticsearch.search.query.ElasticsearchSearchResult;
import org.hibernate.search.engine.search.common.BooleanOperator;
import org.hibernate.search.engine.search.common.ValueModel;
import org.hibernate.search.engine.search.predicate.dsl.MatchPredicateOptionsStep;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.predicate.dsl.SimpleBooleanPredicateClausesCollector;
import org.hibernate.search.engine.search.predicate.dsl.SimpleQueryFlag;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.mapper.pojo.standalone.session.SearchSession;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestQuery;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@ApplicationScoped
@Path("/")
public class SearchService {

    private static final int TITLE_OR_SUMMARY_MAX_SIZE = 32_600;
    private static final int PAGE_SIZE = 50;
    private static final long TOTAL_HIT_COUNT_THRESHOLD = 100;
    private static final int GROUPED_CATEGORIES_SIZE = 30;
    public static final int GROUPED_DOCS_PER_CATEGORY = 9;

    @Inject
    SearchMapping searchMapping;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Search for Guides")
    @Transactional
    @Path("/guides/search")
    public SearchResult<GuideSearchHit> search(@RestQuery @DefaultValue(QuarkusVersions.LATEST) String version,
            @RestQuery List<String> categories,
            @RestQuery String q,
            @RestQuery String origin,
            @RestQuery @DefaultValue("en") Language language,
            @RestQuery @DefaultValue("highlighted") String highlightCssClass,
            @RestQuery @DefaultValue("0") @Min(0) int page,
            @RestQuery List<String> excludeIds) {
        try (var session = searchMapping.createSession()) {
            var result = performSearch(version, categories, q, origin, language, highlightCssClass, page,
                    excludeIds, session);
            if (result.total().hitCountLowerBound() > 0) {
                return new SearchResult<>(result);
            } else {
                SearchResult.Suggestion suggestion = extractSuggestion(result);
                if (suggestion != null) {
                    result = performSearch(version, categories, suggestion.query(), origin, language, highlightCssClass, page,
                            excludeIds, session);
                }
                return new SearchResult<>(result, result.total().hitCountLowerBound() > 0 ? suggestion : null);
            }
        }
    }

    private ElasticsearchSearchResult<GuideSearchHit> performSearch(String version, List<String> categories, String q,
            String origin, Language language, String highlightCssClass, int page,
            List<String> excludeIds, SearchSession session) {
        return session.search(Guide.class)
                .extension(ElasticsearchExtension.get())
                .select(f -> f.composite().from(
                        f.id(),
                        f.field("type"),
                        f.field("status"),
                        f.field("origin"),
                        f.highlight(language.addSuffix("title")).highlighter("highlighter_title_or_summary").optional(),
                        f.highlight(language.addSuffix("summary")).highlighter("highlighter_title_or_summary").optional(),
                        f.field("categories", String.class).list(),
                        f.field("subcategories", String.class).list())
                        .asList(GuideSearchHit::new))
                .where((f, root) -> buildSearchPredicate(f, root, categories, q, origin, language, excludeIds))
                .highlighter(f -> f.fastVector()
                        // Highlighters are going to use spans-with-classes so that we will have more control over styling the visual on the search results screen.
                        .tag("<span class=\"" + highlightCssClass + "\">", "</span>"))
                .highlighter(
                        "highlighter_title_or_summary", f -> f.fastVector()
                                // We want the whole text of the field, regardless of whether it has a match or not.
                                .noMatchSize(TITLE_OR_SUMMARY_MAX_SIZE)
                                .fragmentSize(TITLE_OR_SUMMARY_MAX_SIZE)
                                // We want the whole text as a single fragment
                                .numberOfFragments(1))
                .sort(f -> f.score().then().field(language.addSuffix("title_sort")))
                .routing(QuarkusVersionAndLanguageRoutingBinder.searchKeys(version, language))
                .totalHitCountThreshold(TOTAL_HIT_COUNT_THRESHOLD + (page + 1) * PAGE_SIZE)
                .requestTransformer(context -> {
                    wrapWithCategoriesOrderDemotion(context.body());
                    requestSuggestion(context.body(), q, language, highlightCssClass);
                })
                .fetch(page * PAGE_SIZE, PAGE_SIZE);
    }

    private PredicateFinalStep textMatch(SearchPredicateFactory f, String q, Language language) {
        return f.simpleQueryString()
                .field(language.addSuffix("title")).boost(10.0f)
                .field(language.addSuffix("topics")).boost(10.0f)
                .field(language.addSuffix("keywords")).boost(10.0f)
                .field(language.addSuffix("summary")).boost(5.0f)
                .field(language.addSuffix("fullContent"))
                .field(language.addSuffix("keywords_autocomplete")).boost(1.0f)
                .field(language.addSuffix("title_autocomplete")).boost(1.0f)
                .field(language.addSuffix("summary_autocomplete")).boost(0.5f)
                .field(language.addSuffix("fullContent_autocomplete")).boost(0.1f)
                .matching(q)
                // See: https://github.com/elastic/elasticsearch/issues/39905#issuecomment-471578025
                // while the issue is about stopwords the same problem is observed for synonyms on search-analyzer side.
                // we also add phrase flag so that entire phrases could be searched as well, e.g.: "hibernate search"
                .flags(SimpleQueryFlag.AND, SimpleQueryFlag.OR, SimpleQueryFlag.PHRASE)
                .defaultOperator(BooleanOperator.AND);
    }

    private static MatchPredicateOptionsStep<?> originMatch(SearchPredicateFactory f, String origin) {
        return f.match().field("origin").matching(origin);
    }

    private void buildSearchPredicate(SearchPredicateFactory f, SimpleBooleanPredicateClausesCollector<?, ?> root,
            List<String> categories, String q, String origin, Language language, List<String> excludeIds) {
        root.add(f.matchAll());

        if (categories != null && !categories.isEmpty()) {
            root.add(f.terms().field("categories").matchingAny(categories));
        }

        if (origin != null && !origin.isEmpty()) {
            root.add(f.match().field("origin").matching(origin));
        }

        if (excludeIds != null && !excludeIds.isEmpty()) {
            root.add(f.not(f.id().matchingAny(excludeIds, ValueModel.RAW)));
        }

        if (q != null && !q.isBlank()) {
            root.add(f.or(
                    // Duplicate the query so that we apply a multiplicative boost to quarkus.io guides.
                    // The end result is that a low-relevance match on quarkus.io _can_ be scored
                    // lower than a high-relevance match on quarkiverse.io,
                    // if it's significantly more relevant.
                    // Note that we could, alternatively,
                    // do something like bool().must(textMatch()).should(origin(quarkusio).boost(2f))),
                    // but then the boost would be additive, so we would ignore relative relevance
                    // of quarkus.io/quarkiverse.io results.
                    f.bool().must(textMatch(f, q, language))
                            .filter(originMatch(f, QuarkusIO.QUARKUS_ORIGIN))
                            // Always score lower for compatibility (legacy) guides.
                            // TODO: Maybe we should use a duplicate query with multiplicative boost for this too?
                            .should(f.not(f.match().field(language.addSuffix("topics"))
                                    .matching("compatibility", ValueModel.INDEX))
                                    .boost(50.0f))
                            .boost(2.0f),
                    f.bool().must(textMatch(f, q, language))
                            .filter(originMatch(f, QuarkiverseIO.QUARKIVERSE_ORIGIN))));
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Search for Guides grouped by category")
    @Transactional
    @Path("/guides/search/grouped")
    public GroupedSearchResult searchGrouped(@RestQuery @DefaultValue(QuarkusVersions.LATEST) String version,
            @RestQuery String q,
            @RestQuery String origin,
            @RestQuery @DefaultValue("en") Language language,
            @RestQuery @DefaultValue("highlighted") String highlightCssClass) {
        try (var session = searchMapping.createSession()) {
            var esResult = performGroupedSearch(version, q, origin, language, highlightCssClass, session);
            var result = extractGroupedResults(esResult, language);
            if (!result.categories().isEmpty()) {
                return result;
            } else {
                SearchResult.Suggestion suggestion = extractSuggestion(esResult);
                if (suggestion != null) {
                    esResult = performGroupedSearch(version, suggestion.query(), origin, language, highlightCssClass, session);
                    result = extractGroupedResults(esResult, language);
                }
                return new GroupedSearchResult(result.categories(),
                        !result.categories().isEmpty() ? suggestion : null);
            }
        }
    }

    private ElasticsearchSearchResult<?> performGroupedSearch(String version, String q, String origin, Language language,
            String highlightCssClass, SearchSession session) {
        return session.search(Guide.class)
                .extension(ElasticsearchExtension.get())
                .select(f -> f.score())
                .where((f, root) -> buildSearchPredicate(f, root, null, q, origin, language, null))
                .routing(QuarkusVersionAndLanguageRoutingBinder.searchKeys(version, language))
                .requestTransformer(context -> {
                    wrapWithCategoriesOrderDemotion(context.body());
                    requestGroupedAggregation(context.body(), language, highlightCssClass);
                    requestSuggestion(context.body(), q, language, highlightCssClass);
                })
                .fetch(0);
    }

    private void requestGroupedAggregation(JsonObject payload, Language language, String highlightCssClass) {
        JsonObject aggs = new JsonObject();
        payload.add("aggs", aggs);

        JsonObject categoriesAgg = new JsonObject();
        aggs.add("categories", categoriesAgg);

        JsonObject terms = new JsonObject();
        categoriesAgg.add("terms", terms);
        terms.addProperty("field", "categories");
        terms.addProperty("size", GROUPED_CATEGORIES_SIZE);
        JsonObject order = new JsonObject();
        order.addProperty("max_score", "desc");
        terms.add("order", order);

        JsonObject subAggs = new JsonObject();
        categoriesAgg.add("aggs", subAggs);

        JsonObject maxScore = new JsonObject();
        subAggs.add("max_score", maxScore);
        JsonObject max = new JsonObject();
        maxScore.add("max", max);
        JsonObject script = new JsonObject();
        script.addProperty("source", "_score");
        max.add("script", script);

        JsonObject documents = new JsonObject();
        subAggs.add("documents", documents);
        JsonObject topHits = new JsonObject();
        documents.add("top_hits", topHits);
        topHits.addProperty("size", GROUPED_DOCS_PER_CATEGORY);

        JsonArray sort = new JsonArray();
        topHits.add("sort", sort);
        JsonObject scoreSort = new JsonObject();
        JsonObject scoreSortOrder = new JsonObject();
        scoreSortOrder.addProperty("order", "desc");
        scoreSort.add("_score", scoreSortOrder);
        sort.add(scoreSort);
        JsonObject titleSort = new JsonObject();
        JsonObject titleSortOptions = new JsonObject();
        titleSortOptions.addProperty("order", "asc");
        titleSortOptions.addProperty("unmapped_type", "keyword");
        titleSort.add(language.addSuffix("title_sort"), titleSortOptions);
        sort.add(titleSort);

        JsonArray sourceFields = new JsonArray();
        sourceFields.add("type");
        sourceFields.add("status");
        sourceFields.add("origin");
        sourceFields.add(language.addSuffix("title"));
        sourceFields.add(language.addSuffix("summary"));
        topHits.add("_source", sourceFields);

        JsonObject highlight = new JsonObject();
        topHits.add("highlight", highlight);
        highlight.add("pre_tags", jsonArray("<span class=\"" + highlightCssClass + "\">"));
        highlight.add("post_tags", jsonArray("</span>"));
        JsonObject highlightFields = new JsonObject();
        highlight.add("fields", highlightFields);
        for (String field : List.of(language.addSuffix("title"), language.addSuffix("summary"))) {
            JsonObject fieldConfig = new JsonObject();
            fieldConfig.addProperty("type", "fvh");
            fieldConfig.addProperty("no_match_size", TITLE_OR_SUMMARY_MAX_SIZE);
            fieldConfig.addProperty("fragment_size", TITLE_OR_SUMMARY_MAX_SIZE);
            fieldConfig.addProperty("number_of_fragments", 1);
            highlightFields.add(field, fieldConfig);
        }
    }

    private static JsonArray jsonArray(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private GroupedSearchResult extractGroupedResults(ElasticsearchSearchResult<?> result, Language language) {
        String titleField = language.addSuffix("title");
        String summaryField = language.addSuffix("summary");

        List<CategoryGroup> categories = new ArrayList<>();

        JsonObject aggregations = result.responseBody().getAsJsonObject("aggregations");
        if (aggregations == null) {
            return new GroupedSearchResult(categories);
        }
        JsonArray buckets = aggregations.getAsJsonObject("categories").getAsJsonArray("buckets");

        for (var bucketElement : buckets) {
            JsonObject bucket = bucketElement.getAsJsonObject();
            String category = bucket.get("key").getAsString();
            long docCount = bucket.get("doc_count").getAsLong();

            JsonArray topHits = bucket.getAsJsonObject("documents")
                    .getAsJsonObject("hits").getAsJsonArray("hits");

            List<GroupedGuideHit> hits = new ArrayList<>();
            for (var hitElement : topHits) {
                JsonObject hit = hitElement.getAsJsonObject();
                URI url = URI.create(hit.get("_id").getAsString());
                JsonObject source = hit.getAsJsonObject("_source");
                JsonObject highlightObj = hit.getAsJsonObject("highlight");

                hits.add(new GroupedGuideHit(
                        url,
                        getStringOrNull(source, "type"),
                        getStringOrNull(source, "status"),
                        getStringOrNull(source, "origin"),
                        getHighlightedOrSource(highlightObj, source, titleField),
                        getHighlightedOrSource(highlightObj, source, summaryField)));
            }
            categories.add(new CategoryGroup(category, docCount, hits));
        }
        return new GroupedSearchResult(categories);
    }

    private static String getHighlightedOrSource(JsonObject highlight, JsonObject source, String field) {
        if (highlight != null) {
            JsonArray fragments = highlight.getAsJsonArray(field);
            if (fragments != null && !fragments.isEmpty()) {
                return fragments.get(0).getAsString();
            }
        }
        return getStringOrNull(source, field);
    }

    private static String getStringOrNull(JsonObject obj, String field) {
        var element = obj.get(field);
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }

    private static void wrapWithCategoriesOrderDemotion(JsonObject payload) {
        JsonObject originalQuery = payload.getAsJsonObject("query");
        if (originalQuery == null) {
            return;
        }

        JsonObject functionScore = new JsonObject();
        functionScore.add("query", originalQuery);

        JsonObject fieldValueFactor = new JsonObject();
        fieldValueFactor.addProperty("field", "categoriesOrderDemotion");
        fieldValueFactor.addProperty("modifier", "none");
        fieldValueFactor.addProperty("missing", Guide.NO_CATEGORY_ORDER_DEMOTION);

        JsonArray functions = new JsonArray();
        JsonObject function = new JsonObject();
        function.add("field_value_factor", fieldValueFactor);
        functions.add(function);
        functionScore.add("functions", functions);

        functionScore.addProperty("boost_mode", "multiply");

        JsonObject wrapper = new JsonObject();
        wrapper.add("function_score", functionScore);
        payload.add("query", wrapper);
    }

    private void requestSuggestion(JsonObject payload, String q, Language language, String highlightCssClass) {
        if (q == null || q.isBlank()) {
            return;
        }
        JsonObject suggest = new JsonObject();
        payload.add("suggest", suggest);
        suggest.addProperty("text", q);
        JsonObject suggestion = new JsonObject();
        suggest.add("didYouMean", suggestion);
        JsonObject phrase = new JsonObject();
        suggestion.add("phrase", phrase);
        phrase.addProperty("field", language.addSuffix("fullContent_suggestion"));
        phrase.addProperty("size", 1);
        phrase.addProperty("gram_size", 1);
        JsonObject highlight = new JsonObject();
        phrase.add("highlight", highlight);
        highlight.addProperty("pre_tag", "<span class=\"" + highlightCssClass + "\">");
        highlight.addProperty("post_tag", "</span>");
    }

    private static SearchResult.Suggestion extractSuggestion(ElasticsearchSearchResult<?> result) {
        try {
            JsonObject suggest = result.responseBody().getAsJsonObject("suggest");
            if (suggest != null) {
                JsonArray options = suggest
                        .getAsJsonArray("didYouMean")
                        .get(0).getAsJsonObject()
                        .getAsJsonArray("options");
                if (options != null && !options.isEmpty()) {
                    JsonObject suggestion = options.get(0).getAsJsonObject();
                    return new SearchResult.Suggestion(suggestion.get("text").getAsString(),
                            suggestion.get("highlighted").getAsString());
                }
            }
        } catch (RuntimeException e) {
            // Though it shouldn't happen, just in case we will catch any exceptions and return no suggestions:
            Log.warnf(e, "Failed to extract suggestion: %s", e.getMessage());
        }
        return null;
    }

}
