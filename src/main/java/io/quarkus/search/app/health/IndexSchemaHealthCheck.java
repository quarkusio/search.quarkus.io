package io.quarkus.search.app.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.hibernate.search.mapper.orm.session.SearchSession;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.health.Startup;

/**
 * Checks that the indexes have the correct schema.
 */
@Startup
@Readiness
@ApplicationScoped
public class IndexSchemaHealthCheck implements HealthCheck {
    private static final String NAME = "Index schema";

    @Inject
    SearchSession session;

    @Override
    @Transactional
    public HealthCheckResponse call() {
        try {
            session.scope(Object.class).schemaManager().validate();
        } catch (RuntimeException e) {
            return HealthCheckResponse.builder()
                    .name(NAME).down()
                    .withData("details", "Cannot validate index schema")
                    .withData("exception", e.getMessage())
                    .build();
        }

        return HealthCheckResponse.builder()
                .name(NAME).up()
                .withData("details", "Index schemas are valid")
                .build();
    }
}
