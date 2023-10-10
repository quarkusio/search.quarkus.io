package io.quarkus.search.app;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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

    @Inject
    SearchSession session;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Search for any resource")
    @Transactional
    public SearchResult<SearchHit> search(@RestQuery String q) {
        var result = session.search(Guide.class)
                .select(SearchHit.class)
                .where((f, root) -> {
                    root.add(f.matchAll()); // Default
                    if (q != null && !q.isBlank()) {
                        root.add(f.simpleQueryString()
                                .field("keywords").boost(10.0f)
                                .field("title").boost(7.0f)
                                .field("summary").boost(5.0f)
                                .field("fullContent")
                                .field("keywords_autocomplete").boost(1.0f)
                                .field("title_autocomplete").boost(0.7f)
                                .field("summary_autocomplete").boost(0.5f)
                                .field("fullContent_autocomplete").boost(0.1f)
                                .matching(q));
                    }
                })
                .sort(f -> f.score().then().field("title_sort"))
                .fetch(20);
        return new SearchResult<>(result.total().hitCount(), result.hits());
    }
}
