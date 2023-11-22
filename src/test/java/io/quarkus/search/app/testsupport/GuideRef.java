package io.quarkus.search.app.testsupport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.quarkus.search.app.QuarkusVersions;
import io.quarkus.search.app.fetching.QuarkusIO;

public record GuideRef(String name) {
    private static final List<GuideRef> ALL = new ArrayList<>();

    public static final GuideRef DUPLICATED_CONTEXT = create("duplicated-context");
    public static final GuideRef SPRING_DATA_JPA = create("spring-data-jpa");
    public static final GuideRef HIBERNATE_SEARCH_ORM_ELASTICSEARCH = create("hibernate-search-orm-elasticsearch");
    public static final GuideRef SECURITY_OIDC_BEARER_TOKEN_AUTHENTICATION = create(
            "security-oidc-bearer-token-authentication");
    public static final GuideRef HIBERNATE_ORM_PANACHE = create("hibernate-orm-panache");
    public static final GuideRef HIBERNATE_ORM_PANACHE_KOTLIN = create("hibernate-orm-panache-kotlin");
    public static final GuideRef HIBERNATE_REACTIVE_PANACHE = create("hibernate-reactive-panache");
    public static final GuideRef HIBERNATE_ORM = create("hibernate-orm");
    public static final GuideRef HIBERNATE_REACTIVE = create("hibernate-reactive");
    public static final GuideRef STORK_REFERENCE = create("stork-reference");
    // NOTE: when adding new constants here, don't forget to run the main() method in QuarkusIOFigure
    // to update the QuarkusIO sample in src/test/resources.

    public static GuideRef[] all() {
        return ALL.toArray(GuideRef[]::new);
    }

    public static String[] urls(GuideRef... refs) {
        return urls(QuarkusVersions.LATEST, refs);
    }

    public static String[] urls(String version, GuideRef... refs) {
        return Arrays.stream(refs).map(g -> QuarkusIO.httpPath(version, g.name)).toArray(String[]::new);
    }

    private static GuideRef create(String name) {
        var ref = new GuideRef(name);
        ALL.add(ref);
        return ref;
    }
}
