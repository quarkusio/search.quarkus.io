package io.quarkus.search.app;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkus.search.app.dto.GroupedGuideHit;
import io.quarkus.search.app.dto.GroupedSearchResult;
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
    private static final TypeRef<GroupedSearchResult> GROUPED_SEARCH_RESULT = new TypeRef<>() {
    };
    private static final String GUIDES_SEARCH = "/guides/search";
    private static final String GUIDES_SEARCH_GROUPED = "/guides/search/grouped";

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
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
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
                        GuideRef.HIBERNATE_ORM,
                        GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN)),
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
                        .satisfies(
                                uri -> assertThat(uri).startsWith("https://quarkus.io/version/"
                                        + QuarkusVersions.MAIN + "/guides/")));
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
                        .satisfies(uri -> assertThat(uri).startsWith("https://quarkus.io/version/main/guides/")));
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
                        "Hibernate ORMとElasticsearch/OpenSearchでHibernate Searchを使用");
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
                        "<span class=\"highlighted\">Duplicated</span> <span class=\"highlighted\">context</span>, <span class=\"highlighted\">context</span> <span class=\"highlighted\">locals</span>, <span class=\"highlighted\">asynchronous</span> <span class=\"highlighted\">processing</span> <span class=\"highlighted\">and</span> <span class=\"highlighted\">propagation</span>");
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
                        // fast-vector highlighter will highlight the phrase:
                        "Duplicated context, context locals, <span class=\"highlighted\">asynchronous processing and propagation</span>");
    }

    @Test
    void findEnvVariable() {
        var result = given()
                .queryParam("q", "QUARKUS_DATASOURCE_JDBC_U")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).isNotEmpty();
    }

    @Test
    void findConfigProperty() {
        var result = given()
                .queryParam("q", "quarkus.vertx.eventbus.tcp-keep-alive")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).isNotEmpty();
    }

    @Test
    void findFQCN() {
        var result = given()
                .queryParam("q", "io.quarkus.deployment.pkg.builditem.NativeImageBuildItem")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).isNotEmpty();
    }

    @Test
    void findBuildItem() {
        var result = given()
                .queryParam("q", "NativeImageBuildItem")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).isNotEmpty();
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
                        "<span class=\"highlighted\">Duplicated</span> <span class=\"highlighted\">context</span>, <span class=\"highlighted\">context</span> <span class=\"highlighted\">locals</span>, <span class=\"highlighted\">asynchronous</span> <span class=\"highlighted\">processing</span> <span class=\"highlighted\">and</span> <span class=\"highlighted\">propagation</span>");
    }

    /**
     * Since there are some typos, the search results should include a suggestion with the text that would produce some results.
     */
    @Test
    void suggestion() {
        var result = given()
                .queryParam("q", "hiberante search")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.suggestion().query())
                .isEqualTo("hibernate search");

        result = given()
                .queryParam("q", "aplication")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.suggestion().query())
                .isEqualTo("application");

        result = given()
                .queryParam("q", "Configuring your aplication")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.suggestion().query())
                .isEqualTo("configuring your application");

        result = given()
                .queryParam("q", "vert.ex")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.suggestion().query())
                .isEqualTo("vert.x");
    }

    /**
     * As the query text is already fine, and matches the existing tokens, no suggestion is expected.
     */
    @Test
    void noSuggestion() {
        var result = given()
                .queryParam("q", "hibernate search")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.suggestion()).isNull();
    }

    @Test
    void groupedSearch_queryMatching() {
        var result = given()
                .queryParam("q", "orm")
                .when().get(GUIDES_SEARCH_GROUPED)
                .then()
                .statusCode(200)
                .extract().body().as(GROUPED_SEARCH_RESULT);

        assertThat(result.categories()).isNotEmpty();
        assertThat(result.categories()).allSatisfy(category -> {
            assertThat(category.category()).isNotBlank();
            assertThat(category.hitCount()).isPositive();
            assertThat(category.hits()).isNotEmpty();
            assertThat(category.hits()).allSatisfy(hit -> {
                assertThat(hit.url()).isNotNull();
                assertThat(hit.type()).isNotNull();
                assertThat(hit.origin()).isNotNull();
            });
        });
    }

    @Test
    void groupedSearch_noQuery() {
        var result = when().get(GUIDES_SEARCH_GROUPED)
                .then()
                .statusCode(200)
                .extract().body().as(GROUPED_SEARCH_RESULT);

        assertThat(result.categories()).isNotEmpty();

        var allUrls = result.categories().stream()
                .flatMap(c -> c.hits().stream())
                .map(GroupedGuideHit::url)
                .toList();
        assertThat(allUrls).isNotEmpty();
    }

    @Test
    void groupedSearch_noResults() {
        var result = given()
                .queryParam("q", "termnotmatchinganything")
                .when().get(GUIDES_SEARCH_GROUPED)
                .then()
                .statusCode(200)
                .extract().body().as(GROUPED_SEARCH_RESULT);

        assertThat(result.categories()).isEmpty();
    }

    @Test
    void groupedSearch_hitsHaveTitleAndSummary() {
        var result = given()
                .queryParam("q", "hibernate")
                .when().get(GUIDES_SEARCH_GROUPED)
                .then()
                .statusCode(200)
                .extract().body().as(GROUPED_SEARCH_RESULT);

        assertThat(result.categories()).isNotEmpty();
        var allHits = result.categories().stream()
                .flatMap(c -> c.hits().stream())
                .toList();
        assertThat(allHits).isNotEmpty();
        assertThat(allHits).allSatisfy(hit -> {
            assertThat(hit.title()).isNotBlank();
            assertThat(hit.summary()).isNotNull();
        });
    }

    @Test
    void groupedSearch_highlightTitle() {
        var result = given()
                .queryParam("q", "orm")
                .when().get(GUIDES_SEARCH_GROUPED)
                .then()
                .statusCode(200)
                .extract().body().as(GROUPED_SEARCH_RESULT);

        assertThat(result.categories()).isNotEmpty();
        var allTitles = result.categories().stream()
                .flatMap(c -> c.hits().stream())
                .map(GroupedGuideHit::title)
                .toList();
        assertThat(allTitles)
                .anyMatch(title -> title.contains("<span class=\"highlighted\">"));
    }

    @Test
    void excludeIds_single() {
        URI excludedUrl = GuideRef.HIBERNATE_ORM.url();
        var result = given()
                .queryParam("q", "orm")
                .queryParam("excludeIds", excludedUrl.toString())
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);

        assertThat(result.hits()).extracting(GuideSearchHit::url)
                .doesNotContain(excludedUrl);
        assertThat(result.hits()).isNotEmpty();
    }

    @Test
    void excludeIds_multiple() {
        URI excludedUrl1 = GuideRef.HIBERNATE_ORM.url();
        URI excludedUrl2 = GuideRef.HIBERNATE_ORM_PANACHE.url();
        var result = given()
                .queryParam("q", "orm")
                .queryParam("excludeIds", excludedUrl1.toString())
                .queryParam("excludeIds", excludedUrl2.toString())
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);

        assertThat(result.hits()).extracting(GuideSearchHit::url)
                .doesNotContain(excludedUrl1, excludedUrl2);
        assertThat(result.hits()).isNotEmpty();
    }

    @Test
    void excludeIds_absent() {
        var resultWithout = search("orm");
        var resultWith = given()
                .queryParam("q", "orm")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);

        assertThat(resultWith.hits()).extracting(GuideSearchHit::url)
                .containsExactlyInAnyOrderElementsOf(
                        resultWithout.hits().stream().map(GuideSearchHit::url).toList());
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
