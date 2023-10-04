package io.quarkus.search.app;

import java.util.List;

import org.jboss.resteasy.reactive.RestQuery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;

@ApplicationScoped
@Path("/")
public class SearchService {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Search for any resource")
    public SearchResult<SearchHit> search(@RestQuery String q) {
        return new SearchResult<>(1, List.of(new SearchHit("not-implemented-yet")));
    }
}
