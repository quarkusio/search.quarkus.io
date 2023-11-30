package io.quarkus.search.app;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;

import org.hibernate.Length;
import org.hibernate.search.engine.search.common.BooleanOperator;
import org.hibernate.search.engine.search.highlighter.dsl.HighlighterEncoder;
import org.hibernate.search.mapper.orm.session.SearchSession;

import org.jboss.resteasy.reactive.RestQuery;

import io.quarkus.search.app.dto.GuideSearchHit;
import io.quarkus.search.app.dto.SearchResult;
import io.quarkus.search.app.entity.Guide;

@ApplicationScoped
@Path("/")
public class SearchService {

    private static final Integer PAGE_SIZE = 50;

    @Inject
    SearchSession session;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Search for Guides")
    @Transactional
    @Path("/guides/search")
    public SearchResult<GuideSearchHit> search(@RestQuery @DefaultValue(QuarkusVersions.LATEST) String version,
            @RestQuery List<String> categories,
            @RestQuery String q,
            @RestQuery @DefaultValue("highlighted") String highlightCssClass,
            @RestQuery @DefaultValue("0") int page,
            @RestQuery @DefaultValue("1") int contentSnippets,
            @RestQuery @DefaultValue("100") int contentSnippetsLength) {
        var result = session.search(Guide.class)
                .select(GuideSearchHit.class)
                .where((f, root) -> {
                    // Match all documents by default
                    root.add(f.matchAll());

                    root.add(f.match().field("version").matching(version));

                    if (categories != null && !categories.isEmpty()) {
                        root.add(f.terms().field("categories").matchingAny(categories));
                    }

                    if (q != null && !q.isBlank()) {
                        root.add(f.bool().must(f.simpleQueryString()
                                .field("title").boost(10.0f)
                                .field("topics").boost(10.0f)
                                .field("keywords").boost(10.0f)
                                .field("summary").boost(5.0f)
                                .field("fullContent")
                                .field("keywords_autocomplete").boost(1.0f)
                                .field("title_autocomplete").boost(1.0f)
                                .field("summary_autocomplete").boost(0.5f)
                                .field("fullContent_autocomplete").boost(0.1f)
                                .matching(q)
                                .defaultOperator(BooleanOperator.AND))
                                .should(f.match().field("origin").matching("quarkus").boost(50.0f))
                                .should(f.not(f.match().field("topics").matching("compatibility"))
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
                                // just in case we have any "unsafe" content:
                                .encoder(HighlighterEncoder.HTML)
                                .numberOfFragments(1)
                                .tag("<span class=\"" + highlightCssClass + "\">", "</span>")
                                .boundaryScanner().sentence().end())
                // * If there's no match in the full content we don't want to return anything.
                // * Also content is really huge, so we want to only get small parts of the sentences. We are allowing caller to pick the number of sentences and their length:
                .highlighter("highlighter_content",
                        f -> f.unified().noMatchSize(0).numberOfFragments(contentSnippets).fragmentSize(contentSnippetsLength))
                .sort(f -> f.score().then().field("title_sort"))
                .fetch(page * PAGE_SIZE, PAGE_SIZE);
        return new SearchResult<>(result.total().hitCount(), result.hits());
    }
}
