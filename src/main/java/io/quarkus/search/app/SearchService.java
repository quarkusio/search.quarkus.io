package io.quarkus.search.app;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
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
import io.quarkus.search.app.quarkusio.QuarkusIO;

import io.quarkus.runtime.LaunchMode;

import org.hibernate.Length;
import org.hibernate.search.engine.search.common.BooleanOperator;
import org.hibernate.search.engine.search.common.ValueConvert;
import org.hibernate.search.engine.search.predicate.dsl.SimpleQueryFlag;
import org.hibernate.search.mapper.orm.session.SearchSession;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.jboss.resteasy.reactive.RestQuery;

import io.vertx.ext.web.Router;

@ApplicationScoped
@Path("/")
public class SearchService {

    private static final int PAGE_SIZE = 50;
    private static final long TOTAL_HIT_COUNT_THRESHOLD = 100;
    private static final String MAX_FOR_PERF_MESSAGE = "{jakarta.validation.constraints.Max.message} for performance reasons";

    @Inject
    SearchSession session;

    public void init(@Observes Router router) {
        if (LaunchMode.current().isDevOrTest()) {
            return;
        }
        // DISABLE the index.html route in production
        router.getWithRegex("/(index\\.html)?").order(Integer.MIN_VALUE).handler(rc -> {
            rc.response().setStatusCode(404).end();
        });
    }

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
        var result = session.search(Guide.class)
                .select(f -> f.composite().from(
                        f.id(),
                        f.field("type"),
                        f.field("origin"),
                        f.highlight(language.addSuffix("title")),
                        f.highlight(language.addSuffix("summary")),
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
                        root.add(f.bool().must(f.simpleQueryString()
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
                                .defaultOperator(BooleanOperator.AND))
                                .should(f.match().field("origin").matching(QuarkusIO.QUARKUS_ORIGIN).constantScore()
                                        .boost(1000.0f))
                                .should(f.not(f.match().field(language.addSuffix("topics"))
                                        .matching("compatibility", ValueConvert.NO))
                                        .boost(50.0f)));
                    }
                })
                // * Highlighters are going to use spans-with-classes so that we will have more control over styling the visual on the search results screen.
                // * We give control to the caller on the content snippet length and the number of these fragments
                // * No match size is there to make sure that we are still going to get the text even if the field didn't have a match in it.
                // * The title in the Guide entity is `Length.LONG` long, so we use that as a max value for no-match size, but hopefully nobody writes a title that long...
                .highlighter(
                        f -> f.unified().noMatchSize(Length.LONG).fragmentSize(0)
                                .orderByScore(true)
                                .numberOfFragments(1)
                                .tag("<span class=\"" + highlightCssClass + "\">", "</span>")
                                .boundaryScanner().sentence().end())
                // * If there's no match in the full content we don't want to return anything.
                // * Also content is really huge, so we want to only get small parts of the sentences. We are allowing caller to pick the number of sentences and their length:
                .highlighter("highlighter_content",
                        f -> f.unified().noMatchSize(0).numberOfFragments(contentSnippets).fragmentSize(contentSnippetsLength))
                .sort(f -> f.score().then().field(language.addSuffix("title_sort")))
                .routing(QuarkusVersionAndLanguageRoutingBinder.searchKeys(version, language))
                .totalHitCountThreshold(TOTAL_HIT_COUNT_THRESHOLD + (page + 1) * PAGE_SIZE)
                .fetch(page * PAGE_SIZE, PAGE_SIZE);
        return new SearchResult<>(result);
    }

}
