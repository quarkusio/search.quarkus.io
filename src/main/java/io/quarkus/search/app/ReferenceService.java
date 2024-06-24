package io.quarkus.search.app;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.search.app.cache.MethodNameCacheKeyGenerator;
import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.entity.Language;

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.cache.CacheResult;

import org.hibernate.search.engine.search.aggregation.AggregationKey;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;

import org.eclipse.microprofile.openapi.annotations.Operation;

@ApplicationScoped
@Path("/")
@Transactional
@org.jboss.resteasy.reactive.Cache(maxAge = 120)
public class ReferenceService {

    private static final String REFERENCE_CACHE = "reference-cache";

    @CacheName(REFERENCE_CACHE)
    Cache cache;

    @Inject
    SearchMapping searchMapping;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List available versions")
    @Path("/versions")
    @CacheResult(cacheName = REFERENCE_CACHE, keyGenerator = MethodNameCacheKeyGenerator.class)
    public List<String> versions() {
        return listAllValues("quarkusVersion", QuarkusVersions.COMPARATOR.reversed());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List available languages")
    @Path("/languages")
    @CacheResult(cacheName = REFERENCE_CACHE, keyGenerator = MethodNameCacheKeyGenerator.class)
    public Language[] languages() {
        return Language.values();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List available categories")
    @Path("/categories")
    @CacheResult(cacheName = REFERENCE_CACHE, keyGenerator = MethodNameCacheKeyGenerator.class)
    public List<String> categories() {
        return listAllValues("categories", Comparator.naturalOrder());
    }

    public void invalidateCaches() {
        cache.invalidateAll().subscribe().asCompletionStage().join();
    }

    private List<String> listAllValues(String fieldName, Comparator<String> comparator) {
        try (var session = searchMapping.createSession()) {
            var aggKey = AggregationKey.<Map<String, Long>> of("versions");
            var result = session.search(Guide.class)
                    .where(f -> f.matchAll())
                    .aggregation(aggKey, f -> f.terms().field(fieldName, String.class))
                    .fetch(0);
            return result.aggregation(aggKey).keySet().stream().sorted(comparator).toList();
        }
    }
}
