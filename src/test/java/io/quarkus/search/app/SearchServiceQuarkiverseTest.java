package io.quarkus.search.app;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.quarkus.search.app.dto.GuideSearchHit;
import io.quarkus.search.app.dto.SearchResult;
import io.quarkus.search.app.testsupport.GuideRef;
import io.quarkus.search.app.testsupport.QuarkusIOSample;
import io.quarkus.search.app.testsupport.SetupUtil;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.assertj.core.api.InstanceOfAssertFactories;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.filter.log.LogDetail;

@QuarkusTest
@TestHTTPEndpoint(SearchService.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestProfile(SearchServiceQuarkiverseTest.Profile.class)
@QuarkusIOSample.Setup(filter = QuarkusIOSample.SearchServiceFilterDefinition.class)
class SearchServiceQuarkiverseTest {
    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkiverseio.enabled", "true");
        }
    }

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
                                uri -> assertThat(uri).startsWith("https://docs.quarkiverse.io/")));
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
                                uri -> assertThat(uri).startsWith("https://docs.quarkiverse.io/")));
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
    @Disabled("Since quarkiverse guides are now fetched directly from their site there is no translation for them available anymore")
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
}
