package io.quarkus.search.app;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkus.search.app.dto.GuideSearchHit;
import io.quarkus.search.app.dto.SearchResult;
import io.quarkus.search.app.testsupport.GuideRef;
import io.quarkus.search.app.testsupport.QuarkusIOSample;
import io.quarkus.search.app.testsupport.SetupUtil;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowingConsumer;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.filter.log.LogDetail;

@QuarkusTest
@TestHTTPEndpoint(SearchService.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusIOSample.Setup(filter = QuarkusIOSample.SearchServiceFilterDefinition.class)
class SearchServiceTest {
    private static final TypeRef<SearchResult<GuideSearchHit>> SEARCH_RESULT_SEARCH_HITS = new TypeRef<>() {
    };
    private static final String GUIDES_SEARCH = "/guides/search";

    private SearchResult<GuideSearchHit> search(String term) {
        return given()
                .queryParam("q", term)
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
    }

    @BeforeAll
    void setup() {
        SetupUtil.waitForIndexing(getClass());
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.BODY);
    }

    @Test
    void queryNotMatching() {
        var result = search("termnotmatching");
        assertThat(result.hits()).isEmpty();
        assertThat(result.total().exact()).isEqualTo(0);
    }

    @Test
    void queryMatchingFullTerm() {
        var result = search("orm");
        // We check order in another test
        assertThat(result.hits()).extracting(GuideSearchHit::url).containsExactlyInAnyOrder(GuideRef.urls(
                GuideRef.HIBERNATE_ORM,
                GuideRef.HIBERNATE_ORM_PANACHE,
                GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                GuideRef.HIBERNATE_REACTIVE,
                GuideRef.HIBERNATE_REACTIVE_PANACHE,
                GuideRef.SPRING_DATA_JPA,
                GuideRef.ALL_CONFIG,
                GuideRef.ALL_BUILDITEMS));
        assertThat(result.total().exact()).isEqualTo(9);
    }

    @Test
    void queryMatchingIncludedAdoc() {
        // This property is mentioned in the configuration reference only,
        // not in the main body of the guide,
        // so we can only get a match if we correctly index included asciidoc files
        // (or... the full rendered HTML).
        var result = search("quarkus.hibernate-orm.validate-in-dev-mode");
        assertThat(result.hits()).extracting(GuideSearchHit::url).containsExactlyInAnyOrder(GuideRef.urls(
                GuideRef.HIBERNATE_ORM,
                GuideRef.HIBERNATE_REACTIVE,
                GuideRef.ALL_CONFIG));
    }

    @Test
    void queryMatchingPrefixTerm() {
        var result = search("hiber");
        // We check order in another test
        assertThat(result.hits()).extracting(GuideSearchHit::url).containsExactlyInAnyOrder(GuideRef.urls(
                GuideRef.HIBERNATE_ORM,
                GuideRef.HIBERNATE_ORM_PANACHE,
                GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                GuideRef.HIBERNATE_REACTIVE,
                GuideRef.HIBERNATE_REACTIVE_PANACHE,
                GuideRef.SPRING_DATA_JPA,
                GuideRef.DUPLICATED_CONTEXT,
                GuideRef.ALL_CONFIG,
                GuideRef.ALL_BUILDITEMS));
        assertThat(result.total().exact()).isEqualTo(10);
    }

    @Test
    void queryMatchingTwoTerms() {
        var result = search("orm elasticsearch");
        // We expect an AND by default
        assertThat(result.hits()).extracting(GuideSearchHit::url)
                .containsExactlyInAnyOrder(GuideRef.urls(
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideRef.ALL_CONFIG,
                        GuideRef.ALL_BUILDITEMS));
        assertThat(result.total().exact()).isEqualTo(3);
    }

    @Test
    void queryEmptyString() {
        var result = search("");
        assertThat(result.hits()).extracting(GuideSearchHit::url)
                .containsExactlyInAnyOrder(GuideRef.urls(QuarkusIOSample.SearchServiceFilterDefinition.guides()));
    }

    @Test
    void queryNotProvided() {
        var result = when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::url)
                .containsExactlyInAnyOrder(GuideRef.urls(QuarkusIOSample.SearchServiceFilterDefinition.guides()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://quarkus.io",
            "https://es.quarkus.io",
            "https://cn.quarkus.io",
            "https://ja.quarkus.io",
            "https://pt.quarkus.io",
            "https://quarkus-site-pr-1825-preview.surge.sh",
            "https://quarkus-website-pr-1825-preview.surge.sh",
            "https://quarkus-pr-main-38430-preview.surge.sh"
    })
    void cors_allowed(String origin) {
        given()
                .header("Origin", origin)
                .queryParam("q", "foo")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .header("access-control-allow-origin", origin);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080/guides",
            "https://localhost:8080/guides",
            "https://example.com/guides",
            "https://example.com/",
            "https://my-quarkus.io",
            "https://quarkus-site-pr-1825-preview-surge.sh",
            "https://quarkus-website-pr-1825-preview-surge.sh"
    })
    void cors_denied(String origin) {
        given()
                .header("Origin", origin)
                .queryParam("q", "foo")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(403);
    }

    @ParameterizedTest
    @MethodSource
    void relevance(String query, URI[] expectedGuideUrls) {
        var result = search(query);
        // Using "startsWith" here, because what we want is to have the most relevant hits first.
        // We don't mind that much if there's a trail of not-so-relevant hits.
        assertThat(result.hits()).extracting(GuideSearchHit::url).startsWith(expectedGuideUrls);
    }

    private static List<Arguments> relevance() {
        return List.of(
                // I wonder if we could use something similar to https://stackoverflow.com/a/74737474/5043585
                // to have some sort of weight in the documents and prioritize some of them
                // problem will be to find the right balance because the weight would be always on
                // another option could be to use the keywords to trick some searches
                Arguments.of("orm", GuideRef.urls(
                        // TODO Shouldn't the ORM guide be before Panache?
                        GuideRef.HIBERNATE_ORM_PANACHE,
                        GuideRef.HIBERNATE_ORM,
                        GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideRef.HIBERNATE_REACTIVE_PANACHE,
                        GuideRef.HIBERNATE_REACTIVE)),
                Arguments.of("reactive", GuideRef.urls(
                        GuideRef.HIBERNATE_REACTIVE,
                        GuideRef.HIBERNATE_REACTIVE_PANACHE)),
                Arguments.of("hiber", GuideRef.urls(
                        // TODO Hibernate Reactive/Search should be after ORM...
                        // TODO Shouldn't the ORM guide be before Panache?
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideRef.HIBERNATE_REACTIVE,
                        GuideRef.HIBERNATE_REACTIVE_PANACHE,
                        GuideRef.HIBERNATE_ORM_PANACHE,
                        GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideRef.HIBERNATE_ORM)),
                Arguments.of("jpa", GuideRef.urls(
                        GuideRef.HIBERNATE_ORM,
                        GuideRef.HIBERNATE_ORM_PANACHE,
                        GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideRef.HIBERNATE_REACTIVE_PANACHE // contains a reference to jpa-modelgen
                )),
                Arguments.of("jakarta persistence", GuideRef.urls(
                        GuideRef.HIBERNATE_ORM,
                        GuideRef.HIBERNATE_ORM_PANACHE,
                        GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideRef.HIBERNATE_REACTIVE_PANACHE // contains a reference to jpa-modelgen
                )),
                Arguments.of("search", GuideRef.urls(
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH)),
                Arguments.of("stork", GuideRef.urls(
                        GuideRef.STORK_REFERENCE)),
                Arguments.of("spring data", GuideRef.urls(
                        GuideRef.SPRING_DATA_JPA)));
    }

    @Test
    void projections() {
        var result = search("hiber");
        // We check order in another test
        assertThat(result.hits()).extracting(GuideSearchHit::url).containsExactlyInAnyOrder(GuideRef.urls(
                GuideRef.HIBERNATE_ORM,
                GuideRef.HIBERNATE_ORM_PANACHE,
                GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                GuideRef.HIBERNATE_REACTIVE,
                GuideRef.HIBERNATE_REACTIVE_PANACHE,
                GuideRef.SPRING_DATA_JPA,
                GuideRef.DUPLICATED_CONTEXT,
                GuideRef.ALL_CONFIG,
                GuideRef.ALL_BUILDITEMS));
        assertThat(result.total().exact()).isEqualTo(10);
    }

    @Test
    void version() {
        var result = given()
                .queryParam("q", "orm")
                .queryParam("version", QuarkusVersions.MAIN)
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits())
                .isNotEmpty()
                .allSatisfy(hit -> assertThat(hit).extracting(GuideSearchHit::url, InstanceOfAssertFactories.URI_TYPE)
                        .asString()
                        .satisfiesAnyOf(
                                uri -> assertThat(uri).startsWith("https://quarkus.io/version/"
                                        + QuarkusVersions.MAIN + "/guides/"),
                                uri -> assertThat(uri).startsWith("https://quarkiverse.github.io/quarkiverse-docs")));
        result = given()
                .queryParam("q", "orm")
                .queryParam("version", "main")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits())
                .isNotEmpty()
                .allSatisfy(hit -> assertThat(hit).extracting(GuideSearchHit::url, InstanceOfAssertFactories.URI_TYPE)
                        .asString()
                        .satisfiesAnyOf(
                                uri -> assertThat(uri).startsWith("https://quarkus.io/version/main/guides/"),
                                uri -> assertThat(uri).startsWith("https://quarkiverse.github.io/quarkiverse-docs")));
    }

    @Test
    void quarkiverse() {
        var result = given()
                .queryParam("q", "amazon")
                .queryParam("version", QuarkusVersions.MAIN)
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::url)
                .satisfiesOnlyOnce(
                        uri -> assertThat(uri).asString().contains(GuideRef.QUARKIVERSE_AMAZON_S3.nameBeforeRestRenaming()))
                .satisfiesOnlyOnce(
                        uri -> assertThat(uri).asString()
                                .contains(GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH.nameBeforeRestRenaming()));
    }

    @Test
    void categories() {
        var result = given()
                .queryParam("q", "orm")
                .queryParam("categories", "alt-languages")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::url).containsExactlyInAnyOrder(GuideRef.urls(
                GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN));
    }

    @Test
    void highlight_title() {
        var result = given()
                .queryParam("q", "orm")
                .queryParam("highlightCssClass", "highlighted")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::title).contains(
                "Simplified Hibernate <span class=\"highlighted\">ORM</span> with Panache",
                "Using Hibernate <span class=\"highlighted\">ORM</span> and Jakarta Persistence",
                "Simplified Hibernate <span class=\"highlighted\">ORM</span> with Panache and Kotlin");
    }

    @Test
    void highlight_summary() {
        var result = given()
                .queryParam("q", "orm")
                .queryParam("highlightCssClass", "highlighted-summary")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        AtomicInteger matches = new AtomicInteger(0);
        assertThat(result.hits()).extracting(GuideSearchHit::summary)
                .allSatisfy(hitsHaveCorrectWordHighlighted(matches, "orm", "highlighted-summary"));
        assertThat(matches.get()).isEqualTo(8);
    }

    @Test
    void highlight_content() {
        var result = given()
                .queryParam("q", "orm")
                .queryParam("highlightCssClass", "highlighted-content")
                .queryParam("contentSnippets", "1")
                .queryParam("contentSnippetsLength", "50")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);

        AtomicInteger matches = new AtomicInteger(0);
        assertThat(result.hits()).extracting(GuideSearchHit::content).hasSize(9)
                .allSatisfy(content -> assertThat(content).hasSize(1)
                        .allSatisfy(hitsHaveCorrectWordHighlighted(matches, "orm", "highlighted-content")));
        assertThat(matches.get()).isEqualTo(10);
    }

    @Test
    void highlight_content_tooManySnippets() {
        given()
                .queryParam("q", "orm")
                .queryParam("highlightCssClass", "highlighted-content")
                .queryParam("contentSnippets", "11")
                .queryParam("contentSnippetsLength", "50")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(400);
    }

    @Test
    void highlight_content_snippetsLengthTooHigh() {
        given()
                .queryParam("q", "orm")
                .queryParam("highlightCssClass", "highlighted-content")
                .queryParam("contentSnippets", "2")
                .queryParam("contentSnippetsLength", "201")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(400);
    }

    @Test
    void language() {
        var result = given()
                .queryParam("q", "ガイド")
                .queryParam("language", "ja")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::title)
                .contains("Stork リファレンス<span class=\"highlighted\">ガイド</span>",
                        "Use Hibernate Search with Hibernate ORM and Elasticsearch/OpenSearch");
    }

    @Test
    void language_quarkiverse() {
        var result = given()
                .queryParam("q", "クラウドストレージ") // means "Cloud storage"
                .queryParam("language", "ja")
                .queryParam("version", "main")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::url).satisfiesExactlyInAnyOrder(
                uri -> assertThat(uri.toString()).startsWith(GuideRef.QUARKIVERSE_AMAZON_S3.name()));
    }

    @Test
    void quoteEmptyQuoteTitleTranslation() {
        var result = given()
                // this title has a blank string in a translation file for CN, so we want to look for it and make sure that we won't fail to retrieve the results:
                .queryParam("q", "Duplicated context, context locals, asynchronous processing and propagation")
                .queryParam("language", "cn")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::title)
                .contains(
                        "<span class=\"highlighted\">Duplicated</span> <span class=\"highlighted\">context</span>, <span class=\"highlighted\">context</span> <span class=\"highlighted\">locals</span>, <span class=\"highlighted\">asynchronous</span> <span class=\"highlighted\">processing</span> and <span class=\"highlighted\">propagation</span>");
    }

    @Test
    void searchForPhrase() {
        var result = given()
                .queryParam("q", "\"asynchronous processing and propagation\"")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::title)
                .contains(
                        // unified highlighter will still "highlight" the phrase word by word:
                        "Duplicated context, context locals, <span class=\"highlighted\">asynchronous</span> <span class=\"highlighted\">processing</span> and <span class=\"highlighted\">propagation</span>");
    }

    @Test
    void findEnvVariable() {
        var result = given()
                // the variable that we are "planning" to find is actually QUARKUS_DATASOURCE_JDBC_TRACING_IGNORE_FOR_TRACING
                // But we'll be looking only for a part of it.
                .queryParam("q", "QUARKUS_DATASOURCE_JDBC_TRACING_")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::content)
                // empty set since we are not looking for an entire var name, and our autocomplete on text is only producing grams up to 10 chars
                .containsOnly(Set.of());
    }

    @Test
    void findConfigProperty() {
        var result = given()
                .queryParam("q", "quarkus.websocket.max-frame-size")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::content)
                .containsOnly(
                        Set.of("…Environment variable: QUARKUS_VIRTUAL_THREADS_ENABLED Show more boolean true WebSockets Client Type Default <span class=\"highlighted\">quarkus.websocket.max</span>-<span class=\"highlighted\">frame</span>-<span class=\"highlighted\">size</span>…"));
    }

    @Test
    void findFQCN() {
        var result = given()
                .queryParam("q", "io.quarkus.deployment.pkg.builditem.NativeImageBuildItem")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::content)
                .containsOnly(Set.of(
                        "…io.quarkus.deployment.builditem.nativeimage.NativeImageAllowIncompleteClasspathAggregateBuildItem Do not use directly: use instead. boolean allow No Javadoc found <span class=\"highlighted\">io.quarkus.deployment.pkg.builditem.NativeImageBuildItem</span>…"));
    }

    @Test
    void findBuildItem() {
        var result = given()
                .queryParam("q", "NativeImageBuildItem")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::content)
                .containsOnly(Set.of(
                        "…io.quarkus.deployment.builditem.nativeimage.NativeImageAllowIncompleteClasspathAggregateBuildItem Do not use directly: use instead. boolean allow No Javadoc found <span class=\"highlighted\">io.quarkus.deployment.pkg.builditem.NativeImageBuildItem</span>…"));
    }

    @Test
    void findAllUppercase() {
        var result = given()
                .queryParam("q", "DUPLICATED CONTEXT, CONTEXT LOCALS, ASYNCHRONOUS PROCESSING AND PROPAGATION")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::title)
                .contains(
                        "<span class=\"highlighted\">Duplicated</span> <span class=\"highlighted\">context</span>, <span class=\"highlighted\">context</span> <span class=\"highlighted\">locals</span>, <span class=\"highlighted\">asynchronous</span> <span class=\"highlighted\">processing</span> and <span class=\"highlighted\">propagation</span>");
    }

    private static ThrowingConsumer<String> hitsHaveCorrectWordHighlighted(AtomicInteger matches, String word,
            String cssClass) {
        return sentence -> {
            Matcher matcher = Pattern.compile("<span class=\"" + cssClass + "\">([^<]*)<\\/span>")
                    .matcher(sentence);
            while (matcher.find()) {
                assertThat(matcher.group(1).toLowerCase(Locale.ROOT)).isEqualToIgnoringCase(word);
                matches.incrementAndGet();
            }
        };
    }
}
