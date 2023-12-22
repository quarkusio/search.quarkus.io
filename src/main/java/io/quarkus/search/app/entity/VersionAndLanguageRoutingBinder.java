package io.quarkus.search.app.entity;

import org.hibernate.search.mapper.pojo.bridge.RoutingBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.RoutingBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.RoutingBinder;
import org.hibernate.search.mapper.pojo.bridge.runtime.RoutingBridgeRouteContext;
import org.hibernate.search.mapper.pojo.route.DocumentRoutes;

public class VersionAndLanguageRoutingBinder implements RoutingBinder {
    public static String key(String version, Language language) {
        return version + "/" + language.code;
    }

    @Override
    public void bind(RoutingBindingContext context) {
        context.dependencies()
                .use("version")
                .use("language");

        context.bridge(Guide.class, new GuideRoutingBridge());
    }

    public static class GuideRoutingBridge implements RoutingBridge<Guide> {

        @Override
        public void route(DocumentRoutes routes, Object entityIdentifier, Guide entity,
                RoutingBridgeRouteContext context) {
            routes.addRoute().routingKey(key(entity.version, entity.language));
        }

        @Override
        public void previousRoutes(DocumentRoutes routes, Object entityIdentifier, Guide entity,
                RoutingBridgeRouteContext context) {
            // The route never changes
            route(routes, entityIdentifier, entity, context);
        }
    }
}
