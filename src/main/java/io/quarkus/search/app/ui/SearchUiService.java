package io.quarkus.search.app.ui;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.vertx.ext.web.Router;

@ApplicationScoped
public class SearchUiService {

    @Inject
    SearchUiConfig searchUiConfig;

    public void init(@Observes Router router) {
        if (searchUiConfig.enabled()) {
            return;
        }
        // DISABLE the index.html route in production
        router.getWithRegex("/(index\\.html)?").order(Integer.MIN_VALUE).handler(rc -> {
            rc.response().setStatusCode(404).end();
        });
    }
}
