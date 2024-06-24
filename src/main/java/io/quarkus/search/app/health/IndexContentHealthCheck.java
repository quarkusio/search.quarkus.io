package io.quarkus.search.app.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.health.Startup;

/**
 * Checks that the indexes were populated.
 */
@Startup
@Readiness
@ApplicationScoped
public class IndexContentHealthCheck implements HealthCheck {
    private static final String NAME = "Index content";

    @Inject
    SearchMapping searchMapping;

    @Override
    @Transactional
    public HealthCheckResponse call() {
        long totalHitCount;
        try (var session = searchMapping.createSession()) {
            totalHitCount = session.search(Object.class)
                    .where(f -> f.matchAll())
                    .fetchTotalHitCount();
            // Indexing uses rollover and alias switching so that indexing appears (is?) atomic.
            // If we find one document, we know they are all there.
            // See IndexingService#indexAll
            if (totalHitCount <= 0L) {
                return HealthCheckResponse.builder()
                        .name(NAME).down()
                        .withData("details", "Indexes are empty")
                        .build();
            }
        } catch (RuntimeException e) {
            return HealthCheckResponse.builder()
                    .name(NAME).down()
                    .withData("details", "Cannot reach indexes")
                    .withData("exception", e.getMessage())
                    .build();
        }

        return HealthCheckResponse.builder()
                .name(NAME).up()
                .withData("details", "Indexes contain " + totalHitCount + " elements")
                .build();
    }
}
