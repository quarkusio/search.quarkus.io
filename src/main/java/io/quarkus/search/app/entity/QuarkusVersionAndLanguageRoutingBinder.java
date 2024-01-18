package io.quarkus.search.app.entity;

import java.util.List;

import org.hibernate.search.mapper.pojo.bridge.RoutingBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.RoutingBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.RoutingBinder;
import org.hibernate.search.mapper.pojo.bridge.runtime.RoutingBridgeRouteContext;
import org.hibernate.search.mapper.pojo.route.DocumentRoutes;

public class QuarkusVersionAndLanguageRoutingBinder implements RoutingBinder {
    private static String key(String version, Language language) {
        if (language == null) {
            return version;
        }
        return version + "/" + language.code;
    }

    public static List<String> searchKeys(String version, Language language) {
        return List.of(key(version, language), key(version, null));
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
            routes.addRoute().routingKey(key(entity.quarkusVersion, entity.language));
        }

        @Override
        public void previousRoutes(DocumentRoutes routes, Object entityIdentifier, Guide entity,
                RoutingBridgeRouteContext context) {
            // The route never changes
            route(routes, entityIdentifier, entity, context);
        }
    }
}
