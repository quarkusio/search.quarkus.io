package io.quarkus.search.app.testsupport;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.quarkus.search.app.QuarkusVersions;
import io.quarkus.search.app.quarkusio.QuarkusIO;
import io.quarkus.search.app.quarkusio.QuarkusIOConfig;

public record GuideRef(String name) {
    private static final List<GuideRef> ALL = new ArrayList<>();
    private static final List<GuideRef> LOCAL = new ArrayList<>();
    private static final List<GuideRef> QUARKIVERSE = new ArrayList<>();

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
    public static final GuideRef DEV_SERVICES_REFERENCE = create("dev-services");
    public static final GuideRef RESTEASY_REACTIVE_REFERENCE = create("resteasy-reactive");
    public static final GuideRef VERTX_REFERENCE = create("vertx-reference");
    public static final GuideRef ALL_CONFIG = create("all-config");
    public static final GuideRef QUARKIVERSE_AMAZON_S3 = createQuarkiverse(
            "https://quarkiverse.github.io/quarkiverse-docs/quarkus-amazon-services/dev/amazon-s3.html");
    // NOTE: when adding new constants here, don't forget to run the main() method in QuarkusIOFigure
    // to update the QuarkusIO sample in src/test/resources.

    public static GuideRef[] local() {
        return LOCAL.toArray(GuideRef[]::new);
    }

    public static GuideRef[] quarkiverse() {
        return QUARKIVERSE.toArray(GuideRef[]::new);
    }

    public static GuideRef[] all() {
        return ALL.toArray(GuideRef[]::new);
    }

    public static URI[] urls(GuideRef... refs) {
        return urls(QuarkusVersions.LATEST, refs);
    }

    public static URI[] urls(String version, GuideRef... refs) {
        return Arrays.stream(refs)
                .map(g -> QuarkusIO.httpUrl(QuarkusIOConfig.WEB_URI_DEFAULT, version, g.name))
                .toArray(URI[]::new);
    }

    private static GuideRef create(String name) {
        var ref = new GuideRef("/guides/" + name);
        LOCAL.add(ref);
        ALL.add(ref);
        return ref;
    }

    private static GuideRef createQuarkiverse(String name) {
        var ref = new GuideRef(name);
        QUARKIVERSE.add(ref);
        ALL.add(ref);
        return ref;
    }
}
