package io.quarkus.search.app;

/**
 * Constants related to the dataset in test/data.
 */
public final class DatasetConstants {
    private DatasetConstants() {
    }

    public final class GuideIds {
        private GuideIds() {
        }

        public static final String DUPLICATED_CONTEXT = "/guides/duplicated-context";
        public static final String SPRING_DATA_JPA = "/guides/spring-data-jpa";
        public static final String HIBERNATE_SEARCH_ORM_ELASTICSEARCH = "/guides/hibernate-search-orm-elasticsearch";
        public static final String SECURITY_OIDC_BEARER_TOKEN_AUTHENTICATION = "/guides/security-oidc-bearer-token-authentication";
        public static final String HIBERNATE_ORM_PANACHE = "/guides/hibernate-orm-panache";
        public static final String HIBERNATE_ORM_PANACHE_KOTLIN = "/guides/hibernate-orm-panache-kotlin";
        public static final String HIBERNATE_REACTIVE_PANACHE = "/guides/hibernate-reactive-panache";
        public static final String HIBERNATE_ORM = "/guides/hibernate-orm";
        public static final String HIBERNATE_REACTIVE = "/guides/hibernate-reactive";
        public static final String STORK_REFERENCE = "/guides/stork-reference";

        public static final String[] ALL = new String[] {
                DUPLICATED_CONTEXT,
                SPRING_DATA_JPA,
                HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                SECURITY_OIDC_BEARER_TOKEN_AUTHENTICATION,
                HIBERNATE_ORM_PANACHE,
                HIBERNATE_ORM_PANACHE_KOTLIN,
                HIBERNATE_REACTIVE_PANACHE,
                HIBERNATE_ORM,
                HIBERNATE_REACTIVE,
                STORK_REFERENCE
        };
    }

}
