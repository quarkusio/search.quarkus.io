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

import org.hibernate.search.mapper.orm.session.SearchSession;

import org.jboss.resteasy.reactive.RestQuery;

import io.quarkus.search.app.dto.SearchHit;
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
    @Operation(summary = "Search for any resource")
    @Transactional
    public SearchResult<SearchHit> search(@RestQuery @DefaultValue(QuarkusVersions.LATEST) String version,
            @RestQuery List<String> categories,
            @RestQuery String q,
            @RestQuery @DefaultValue("0") int page) {
        var result = session.search(Guide.class)
                .select(SearchHit.class)
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
                                .matching(q))
                                .should(f.not(f.match().field("topics").matching("compatibility"))
                                        .boost(50.0f)));
                    }
                })
                .sort(f -> f.score().then().field("title_sort"))
                .fetch(page * PAGE_SIZE, PAGE_SIZE);
        return new SearchResult<>(result.total().hitCount(), result.hits());
    }
}
