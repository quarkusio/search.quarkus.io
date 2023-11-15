package io.quarkus.search.app;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.search.app.dto.SearchHit;
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
    private static final TypeRef<SearchResult<SearchHit>> SEARCH_RESULT_SEARCH_HITS = new TypeRef<>() {
    };

    protected int managementPort() {
        if (getClass().getName().endsWith("IT")) {
            return 9000;
        } else {
            return 9001;
        }
    }

    private SearchResult<SearchHit> search(String term) {
        return given()
                .queryParam("q", term)
                .when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
    }

    @BeforeAll
    void waitForIndexing() {
        Awaitility.await().untilAsserted(() -> {
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
        assertThat(result.hits()).extracting(SearchHit::id).containsExactlyInAnyOrder(GuideRef.ids(
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
        assertThat(result.hits()).extracting(SearchHit::id).containsExactlyInAnyOrder(GuideRef.ids(
                GuideRef.HIBERNATE_ORM, GuideRef.HIBERNATE_REACTIVE));
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void queryMatchingPrefixTerm() {
        var result = search("hiber");
        // We check order in another test
        assertThat(result.hits()).extracting(SearchHit::id).containsExactlyInAnyOrder(GuideRef.ids(
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
        assertThat(result.hits()).extracting(SearchHit::id)
                .containsExactlyInAnyOrder(GuideRef.ids(GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH));
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void queryEmptyString() {
        var result = search("");
        assertThat(result.hits()).extracting(SearchHit::id)
                .containsExactlyInAnyOrder(GuideRef.ids(GuideRef.all()));
        assertThat(result.total()).isEqualTo(10);
    }

    @Test
    void queryNotProvided() {
        var result = when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(SearchHit::id)
                .containsExactlyInAnyOrder(GuideRef.ids(GuideRef.all()));
        assertThat(result.total()).isEqualTo(10);
    }

    @ParameterizedTest
    @MethodSource
    void relevance(String query, String[] expectedGuideIds) {
        var result = search(query);
        // Using "startsWith" here, because what we want is to have the most relevant hits first.
        // We don't mind that much if there's a trail of not-so-relevant hits.
        assertThat(result.hits()).extracting(SearchHit::id).startsWith(expectedGuideIds);
    }

    private static List<Arguments> relevance() {
        return List.of(
                // I wonder if we could use something similar to https://stackoverflow.com/a/74737474/5043585
                // to have some sort of weight in the documents and prioritize some of them
                // problem will be to find the right balance because the weight would be always on
                // another option could be to use the keywords to trick some searches
                Arguments.of("orm", GuideRef.ids(
                        // TODO Shouldn't the ORM guide be before Panache?
                        GuideRef.HIBERNATE_ORM_PANACHE,
                        GuideRef.HIBERNATE_ORM,
                        GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideRef.HIBERNATE_REACTIVE_PANACHE,
                        GuideRef.HIBERNATE_REACTIVE,
                        GuideRef.SPRING_DATA_JPA)),
                Arguments.of("reactive", GuideRef.ids(
                        GuideRef.HIBERNATE_REACTIVE_PANACHE,
                        GuideRef.HIBERNATE_REACTIVE,
                        GuideRef.DUPLICATED_CONTEXT, // contains "Hibernate Reactive"
                        GuideRef.HIBERNATE_ORM_PANACHE,
                        GuideRef.STORK_REFERENCE,
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideRef.HIBERNATE_ORM,
                        GuideRef.SPRING_DATA_JPA)),
                Arguments.of("hiber", GuideRef.ids(
                        // TODO Hibernate Reactive/Search should be after ORM...
                        // TODO Shouldn't the ORM guide be before Panache?
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideRef.HIBERNATE_REACTIVE,
                        GuideRef.HIBERNATE_REACTIVE_PANACHE,
                        GuideRef.HIBERNATE_ORM_PANACHE,
                        GuideRef.HIBERNATE_ORM,
                        GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideRef.DUPLICATED_CONTEXT, // contains "Hibernate Reactive"
                        GuideRef.SPRING_DATA_JPA)),
                Arguments.of("jpa", GuideRef.ids(
                        // TODO we'd probably want ORM before Panache?
                        GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideRef.HIBERNATE_REACTIVE_PANACHE, // contains a reference to jpa-modelgen
                        GuideRef.HIBERNATE_ORM_PANACHE,
                        GuideRef.HIBERNATE_ORM,
                        GuideRef.SPRING_DATA_JPA)),
                Arguments.of("search", GuideRef.ids(
                        GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH)),
                Arguments.of("stork", GuideRef.ids(
                        GuideRef.STORK_REFERENCE)),
                Arguments.of("spring data", GuideRef.ids(
                        GuideRef.SPRING_DATA_JPA)));
    }

    @Test
    void projections() {
        var result = search("hiber");
        // We check order in another test
        assertThat(result.hits()).extracting(SearchHit::id).containsExactlyInAnyOrder(GuideRef.ids(
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
                .when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits())
                .isNotEmpty()
                .allSatisfy(hit -> assertThat(hit).extracting(SearchHit::id, InstanceOfAssertFactories.STRING)
                        .startsWith("/version/" + QuarkusIOSample.SAMPLED_NON_LATEST_VERSION + "/guides/"));
        result = given()
                .queryParam("q", "orm")
                .queryParam("version", "main")
                .when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits())
                .isNotEmpty()
                .allSatisfy(hit -> assertThat(hit).extracting(SearchHit::id, InstanceOfAssertFactories.STRING)
                        .startsWith("/version/main/guides/"));
    }

    @Test
    void categories() {
        var result = given()
                .queryParam("q", "orm")
                .queryParam("categories", "alt-languages")
                .when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(SearchHit::id).containsExactlyInAnyOrder(GuideRef.ids(
                GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN));
    }
}
