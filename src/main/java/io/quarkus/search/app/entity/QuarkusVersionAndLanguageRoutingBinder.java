package io.quarkus.search.app.entity;

import java.util.List;

import io.quarkus.search.app.quarkiverseio.QuarkiverseIO;
import io.quarkus.search.app.quarkusio.QuarkusIO;

import org.hibernate.search.mapper.pojo.bridge.RoutingBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.RoutingBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.RoutingBinder;
import org.hibernate.search.mapper.pojo.bridge.runtime.RoutingBridgeRouteContext;
import org.hibernate.search.mapper.pojo.route.DocumentRoutes;

public class QuarkusVersionAndLanguageRoutingBinder implements RoutingBinder {
    private static String key(String version, Language language) {
        return key(version, language, QuarkusIO.QUARKUS_ORIGIN);
    }

    private static String key(String version, Language language, String origin) {
        StringBuilder key = new StringBuilder();
        key.append(origin);
        if (version != null) {
            key.append("/").append(version);
        }
        if (language != null) {
            key.append("/").append(language.code);
        }
        return key.toString();
    }

    public static List<String> searchKeys(String version, Language language) {
        return List.of(key(version, language), key(version, null), key(null, null, QuarkiverseIO.QUARKIVERSE_ORIGIN));
    }

    @Override
    public void bind(RoutingBindingContext context) {
        context.dependencies()
                .use("quarkusVersion")
                .use("language");

        context.bridge(Guide.class, new GuideRoutingBridge());
    }

    public static class GuideRoutingBridge implements RoutingBridge<Guide> {

        @Override
        public void route(DocumentRoutes routes, Object entityIdentifier, Guide entity,
                RoutingBridgeRouteContext context) {
            if (QuarkiverseIO.QUARKIVERSE_ORIGIN.equals(entity.origin)) {
                routes.addRoute().routingKey(key(null, null, QuarkiverseIO.QUARKIVERSE_ORIGIN));
            } else {
                routes.addRoute().routingKey(key(entity.quarkusVersion, entity.language));
            }
        }

        @Override
        public void previousRoutes(DocumentRoutes routes, Object entityIdentifier, Guide entity,
                RoutingBridgeRouteContext context) {
            // The route never changes
            route(routes, entityIdentifier, entity, context);
        }
    }
}
