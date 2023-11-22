package io.quarkus.search.app;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowingConsumer;
import org.awaitility.Awaitility;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.search.app.dto.GuideSearchHit;
import io.quarkus.search.app.dto.SearchResult;
import io.quarkus.search.app.testsupport.GuideRef;
import io.quarkus.search.app.testsupport.QuarkusIOSample;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.filter.log.LogDetail;

@QuarkusTest
@TestHTTPEndpoint(SearchService.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusIOSample.Setup
class SearchServiceTest {
    private static final TypeRef<SearchResult<GuideSearchHit>> SEARCH_RESULT_SEARCH_HITS = new TypeRef<>() {
    };
    private static final String GUIDES_SEARCH = "/guides/search";

    protected int managementPort() {
        if (getClass().getName().endsWith("IT")) {
            return 9000;
        } else {
            return 9001;
        }
    }

    private SearchResult<GuideSearchHit> search(String term) {
        return given()
                .queryParam("q", term)
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
    }

    @BeforeAll
    void waitForIndexing() {
        Awaitility.await().timeout(Duration.ofMinutes(1)).untilAsserted(() -> {
            when().get("http://localhost:" + managementPort() + "/q/health/ready")
                    .then()
                    .statusCode(200);
        });
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.BODY);
    }

    @Test
    void queryNotMatching() {
        var result = search("termnotmatching");
        assertThat(result.hits()).isEmpty();
        assertThat(result.total()).isEqualTo(0);
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
                GuideRef.SPRING_DATA_JPA));
        assertThat(result.total()).isEqualTo(7);
    }

    @Test
    void queryMatchingIncludedAdoc() {
        // This property is mentioned in the configuration reference only,
        // not in the main body of the guide,
        // so we can only get a match if we correctly index included asciidoc files
        // (or... the full rendered HTML).
        var result = search("quarkus.hibernate-orm.validate-in-dev-mode");
        assertThat(result.hits()).extracting(GuideSearchHit::url).containsExactlyInAnyOrder(GuideRef.urls(
                GuideRef.HIBERNATE_ORM, GuideRef.HIBERNATE_REACTIVE));
        assertThat(result.total()).isEqualTo(2);
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
                GuideRef.DUPLICATED_CONTEXT));
        assertThat(result.total()).isEqualTo(8);
    }

    @Test
    void queryMatchingTwoTerms() {
        var result = search("orm elasticsearch");
        // We expect an AND by default
        assertThat(result.hits()).extracting(GuideSearchHit::url)
                .containsExactlyInAnyOrder(GuideRef.urls(GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH));
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void queryEmptyString() {
        var result = search("");
        assertThat(result.hits()).extracting(GuideSearchHit::url)
                .containsExactlyInAnyOrder(GuideRef.urls(GuideRef.all()));
        assertThat(result.total()).isEqualTo(10);
    }

    @Test
    void queryNotProvided() {
        var result = when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(GuideSearchHit::url)
                .containsExactlyInAnyOrder(GuideRef.urls(GuideRef.all()));
        assertThat(result.total()).isEqualTo(10);
    }

    @ParameterizedTest
    @MethodSource
    void relevance(String query, String[] expectedGuideUrls) {
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
                        GuideRef.HIBERNATE_REACTIVE_PANACHE,
                        GuideRef.HIBERNATE_REACTIVE,
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideRef.SPRING_DATA_JPA)),
                Arguments.of("reactive", GuideRef.urls(
                        GuideRef.HIBERNATE_REACTIVE,
                        GuideRef.HIBERNATE_REACTIVE_PANACHE,
                        GuideRef.DUPLICATED_CONTEXT, // contains "Hibernate Reactive"
                        GuideRef.HIBERNATE_ORM_PANACHE,
                        GuideRef.STORK_REFERENCE,
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideRef.HIBERNATE_ORM,
                        GuideRef.SPRING_DATA_JPA)),
                Arguments.of("hiber", GuideRef.urls(
                        // TODO Hibernate Reactive/Search should be after ORM...
                        // TODO Shouldn't the ORM guide be before Panache?
                        GuideRef.HIBERNATE_REACTIVE,
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideRef.HIBERNATE_REACTIVE_PANACHE,
                        GuideRef.HIBERNATE_ORM_PANACHE,
                        GuideRef.HIBERNATE_ORM,
                        GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideRef.DUPLICATED_CONTEXT, // contains "Hibernate Reactive"
                        GuideRef.SPRING_DATA_JPA)),
                Arguments.of("jpa", GuideRef.urls(
                        // TODO we'd probably want ORM before Panache?
                        GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideRef.HIBERNATE_REACTIVE_PANACHE, // contains a reference to jpa-modelgen
                        GuideRef.HIBERNATE_ORM_PANACHE,
                        GuideRef.HIBERNATE_ORM,
                        GuideRef.SPRING_DATA_JPA)),
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
                GuideRef.DUPLICATED_CONTEXT));
        assertThat(result.total()).isEqualTo(8);
    }

    @Test
    void version() {
        var result = given()
                .queryParam("q", "orm")
                .queryParam("version", QuarkusIOSample.SAMPLED_NON_LATEST_VERSION)
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits())
                .isNotEmpty()
                .allSatisfy(hit -> assertThat(hit).extracting(GuideSearchHit::url, InstanceOfAssertFactories.STRING)
                        .asString()
                        .startsWith("/version/" + QuarkusIOSample.SAMPLED_NON_LATEST_VERSION + "/guides/"));
        result = given()
                .queryParam("q", "orm")
                .queryParam("version", "main")
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits())
                .isNotEmpty()
                .allSatisfy(hit -> assertThat(hit).extracting(GuideSearchHit::url, InstanceOfAssertFactories.STRING)
                        .asString()
                        .startsWith("/version/main/guides/"));
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
    void highlightTitle() {
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
    void highlightSummary() {
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
        assertThat(matches.get()).isEqualTo(7);
    }

    @Test
    void highlightContent() {
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
        assertThat(result.hits()).extracting(GuideSearchHit::content).hasSize(7)
                .allSatisfy(content -> assertThat(content).hasSize(1)
                        .allSatisfy(hitsHaveCorrectWordHighlighted(matches, "orm", "highlighted-content")));
        assertThat(matches.get()).isEqualTo(8);
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
