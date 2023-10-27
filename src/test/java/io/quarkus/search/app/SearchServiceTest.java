package io.quarkus.search.app;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.search.app.DatasetConstants.GuideIds;
import io.quarkus.search.app.dto.SearchHit;
import io.quarkus.search.app.dto.SearchResult;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;

@QuarkusTest
@TestHTTPEndpoint(SearchService.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
        assertThat(result.hits()).extracting(SearchHit::id).containsExactlyInAnyOrder(
                DatasetConstants.GuideIds.HIBERNATE_ORM,
                GuideIds.HIBERNATE_ORM_PANACHE,
                GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN,
                GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                GuideIds.HIBERNATE_REACTIVE,
                GuideIds.HIBERNATE_REACTIVE_PANACHE,
                GuideIds.SPRING_DATA_JPA);
        assertThat(result.total()).isEqualTo(7);
    }

    @Test
    void queryMatchingPrefixTerm() {
        var result = search("hiber");
        // We check order in another test
        assertThat(result.hits()).extracting(SearchHit::id).containsExactlyInAnyOrder(
                GuideIds.HIBERNATE_ORM,
                GuideIds.HIBERNATE_ORM_PANACHE,
                GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN,
                GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                GuideIds.HIBERNATE_REACTIVE,
                GuideIds.HIBERNATE_REACTIVE_PANACHE,
                GuideIds.SPRING_DATA_JPA,
                GuideIds.DUPLICATED_CONTEXT);
        assertThat(result.total()).isEqualTo(8);
    }

    @Test
    void queryEmptyString() {
        var result = search("");
        assertThat(result.hits()).extracting(SearchHit::id)
                .containsExactlyInAnyOrder(DatasetConstants.GuideIds.ALL);
        assertThat(result.total()).isEqualTo(10);
    }

    @Test
    void queryNotProvided() {
        var result = when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits()).extracting(SearchHit::id)
                .containsExactlyInAnyOrder(DatasetConstants.GuideIds.ALL);
        assertThat(result.total()).isEqualTo(10);
    }

    @ParameterizedTest
    @MethodSource
    void relevance(String query, List<String> expectedGuideIds) {
        var result = search(query);
        // Using "startsWith" here, because what we want is to have the most relevant hits first.
        // We don't mind that much if there's a trail of not-so-relevant hits.
        assertThat(result.hits()).extracting(SearchHit::id).startsWith(
                expectedGuideIds.toArray(String[]::new));
    }

    private static List<Arguments> relevance() {
        return List.of(
                // I wonder if we could use something similar to https://stackoverflow.com/a/74737474/5043585
                // to have some sort of weight in the documents and prioritize some of them
                // problem will be to find the right balance because the weight would be always on
                // another option could be to use the keywords to trick some searches
                Arguments.of("orm", List.of(
                        // TODO Shouldn't the ORM guide be before Panache?
                        GuideIds.HIBERNATE_ORM_PANACHE,
                        GuideIds.HIBERNATE_ORM,
                        GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideIds.HIBERNATE_REACTIVE_PANACHE,
                        GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideIds.HIBERNATE_REACTIVE,
                        GuideIds.SPRING_DATA_JPA)),
                Arguments.of("reactive", List.of(
                        GuideIds.HIBERNATE_REACTIVE_PANACHE,
                        GuideIds.HIBERNATE_REACTIVE,
                        GuideIds.DUPLICATED_CONTEXT, // contains "Hibernate Reactive"
                        GuideIds.HIBERNATE_ORM_PANACHE,
                        GuideIds.STORK_REFERENCE,
                        GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideIds.HIBERNATE_ORM,
                        GuideIds.SPRING_DATA_JPA)),
                Arguments.of("hiber", List.of(
                        // TODO Hibernate Reactive/Search should be after ORM...
                        // TODO Shouldn't the ORM guide be before Panache?
                        GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideIds.HIBERNATE_REACTIVE_PANACHE,
                        GuideIds.HIBERNATE_ORM_PANACHE,
                        GuideIds.HIBERNATE_ORM,
                        GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideIds.HIBERNATE_REACTIVE,
                        GuideIds.DUPLICATED_CONTEXT, // contains "Hibernate Reactive"
                        GuideIds.SPRING_DATA_JPA)),
                Arguments.of("jpa", List.of(
                        // TODO we'd probably want ORM before Panache?
                        GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideIds.HIBERNATE_ORM_PANACHE,
                        GuideIds.HIBERNATE_REACTIVE_PANACHE, // contains a reference to jpa-modelgen
                        GuideIds.HIBERNATE_ORM,
                        GuideIds.SPRING_DATA_JPA)),
                Arguments.of("search", List.of(
                        GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH)),
                Arguments.of("stork", List.of(
                        GuideIds.STORK_REFERENCE)),
                Arguments.of("spring data", List.of(
                        GuideIds.SPRING_DATA_JPA)));
    }

    @Test
    void projections() {
        var result = search("hiber");
        // We check order in another test
        assertThat(result.hits()).extracting(SearchHit::id).containsExactlyInAnyOrder(
                GuideIds.HIBERNATE_ORM,
                GuideIds.HIBERNATE_ORM_PANACHE,
                GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN,
                GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                GuideIds.HIBERNATE_REACTIVE,
                GuideIds.HIBERNATE_REACTIVE_PANACHE,
                GuideIds.SPRING_DATA_JPA,
                GuideIds.DUPLICATED_CONTEXT);
        assertThat(result.total()).isEqualTo(8);
    }

    @Test
    void version() {
        var result = given()
                .queryParam("q", "orm")
                .queryParam("version", "2.7")
                .when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.hits())
                .isNotEmpty()
                .allSatisfy(hit -> assertThat(hit).extracting(SearchHit::id, InstanceOfAssertFactories.STRING)
                        .startsWith("/version/2.7/guides/"));
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
        assertThat(result.hits()).extracting(SearchHit::id).containsExactlyInAnyOrder(
                GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN);
    }
}
