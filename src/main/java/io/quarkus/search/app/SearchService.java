package io.quarkus.search.app;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.search.app.dto.GuideSearchHit;
import io.quarkus.search.app.dto.SearchResult;
import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.entity.QuarkusVersionAndLanguageRoutingBinder;
import io.quarkus.search.app.quarkiverseio.QuarkiverseIO;
import io.quarkus.search.app.quarkusio.QuarkusIO;

import org.hibernate.search.backend.elasticsearch.ElasticsearchExtension;
import org.hibernate.search.engine.search.common.BooleanOperator;
import org.hibernate.search.engine.search.common.ValueModel;
import org.hibernate.search.engine.search.predicate.dsl.MatchPredicateOptionsStep;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.predicate.dsl.SimpleQueryFlag;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestQuery;

import com.google.gson.JsonObject;

@ApplicationScoped
@Path("/")
public class SearchService {

    private static final int TITLE_OR_SUMMARY_MAX_SIZE = 32_600;
    private static final int PAGE_SIZE = 50;
    private static final long TOTAL_HIT_COUNT_THRESHOLD = 100;
    private static final String MAX_FOR_PERF_MESSAGE = "{jakarta.validation.constraints.Max.message} for performance reasons";

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
            @RestQuery @DefaultValue("1") @Min(0) @Max(value = 10, message = MAX_FOR_PERF_MESSAGE) int contentSnippets,
            @RestQuery @DefaultValue("100") @Min(0) @Max(value = 200, message = MAX_FOR_PERF_MESSAGE) int contentSnippetsLength) {
        try (var session = searchMapping.createSession()) {
            var result = session.search(Guide.class)
                    .extension(ElasticsearchExtension.get())
                    .select(f -> f.composite().from(
                            f.id(),
                            f.field("type"),
                            f.field("origin"),
                            f.highlight(language.addSuffix("title")).highlighter("highlighter_title_or_summary").single(),
                            f.highlight(language.addSuffix("summary")).highlighter("highlighter_title_or_summary").single(),
                            f.highlight(language.addSuffix("fullContent")).highlighter("highlighter_content"))
                            .asList(GuideSearchHit::new))
                    .where((f, root) -> {
                        // Match all documents by default
                        root.add(f.matchAll());

                        if (categories != null && !categories.isEmpty()) {
                            root.add(f.terms().field("categories").matchingAny(categories));
                        }

                        if (origin != null && !origin.isEmpty()) {
                            root.add(f.match().field("origin").matching(origin));
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
                    })
                    .highlighter(f -> f.fastVector()
                            // Highlighters are going to use spans-with-classes so that we will have more control over styling the visual on the search results screen.
                            .tag("<span class=\"" + highlightCssClass + "\">", "</span>"))
                    .highlighter("highlighter_title_or_summary", f -> f.fastVector()
                            // We want the whole text of the field, regardless of whether it has a match or not.
                            .noMatchSize(TITLE_OR_SUMMARY_MAX_SIZE)
                            .fragmentSize(TITLE_OR_SUMMARY_MAX_SIZE)
                            // We want the whole text as a single fragment
                            .numberOfFragments(1))
                    .highlighter("highlighter_content", f -> f.fastVector()
                            // If there's no match in the full content we don't want to return anything.
                            .noMatchSize(0)
                            // Content is really huge, so we want to only get small parts of the sentences.
                            // We give control to the caller on the content snippet length and the number of these fragments
                            .numberOfFragments(contentSnippets)
                            .fragmentSize(contentSnippetsLength)
                            // The rest of fragment configuration is static
                            .orderByScore(true)
                            // We don't use sentence boundaries because those can result in huge fragments
                            .boundaryScanner().chars().boundaryMaxScan(10).end())
                    .sort(f -> f.score().then().field(language.addSuffix("title_sort")))
                    .routing(QuarkusVersionAndLanguageRoutingBinder.searchKeys(version, language))
                    .totalHitCountThreshold(TOTAL_HIT_COUNT_THRESHOLD + (page + 1) * PAGE_SIZE)
                    .requestTransformer(context -> requestSuggestion(context.body(), q, language, highlightCssClass))
                    .fetch(page * PAGE_SIZE, PAGE_SIZE);
            return new SearchResult<>(result);
        }
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

}
