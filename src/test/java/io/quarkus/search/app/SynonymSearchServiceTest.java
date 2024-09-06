package io.quarkus.search.app;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import io.quarkus.search.app.dto.GuideSearchHit;
import io.quarkus.search.app.dto.SearchResult;
import io.quarkus.search.app.testsupport.QuarkusIOSample;
import io.quarkus.search.app.testsupport.SetupUtil;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.filter.log.LogDetail;

@QuarkusTest
@TestHTTPEndpoint(SearchService.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusIOSample.Setup(filter = QuarkusIOSample.SearchServiceSynonymsFilterDefinition.class)
class SynonymSearchServiceTest {
    private static final TypeRef<SearchResult<GuideSearchHit>> SEARCH_RESULT_SEARCH_HITS = new TypeRef<>() {
    };
    private static final String GUIDES_SEARCH = "guides/search";

    @BeforeAll
    void setup() {
        SetupUtil.waitForIndexing(getClass());
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.BODY);
    }

    @ParameterizedTest
    @MethodSource
    void synonymsTitle(String query, Set<String> expectedTitleHighlights) {
        var hits = searchHitSearchResult(query).hits();
        assertThat(expectedTitleHighlights)
                .allSatisfy(expectedTitleHighlight -> {
                    assertThat(hits)
                            .extracting(GuideSearchHit::title)
                            .anySatisfy(hitTitle -> assertThat(hitTitle).containsIgnoringCase(expectedTitleHighlight));
                });
    }

    private List<? extends Arguments> synonymsTitle() {
        return List.of(
                Arguments.of("REST Development Service",
                        Set.of("<span class=\"highlighted\">Dev Services</span>")),
                Arguments.of("rest easy",
                        Set.of("<span class=\"highlighted\">REST</span>", "<span class=\"highlighted\">RESTEasy</span>")),
                Arguments.of("vertx",
                        Set.of("<span class=\"highlighted\">Vert.x</span>")),
                Arguments.of("rest api",
                        Set.of("<span class=\"highlighted\">REST</span>", "<span class=\"highlighted\">RESTEasy</span>")),
                Arguments.of("config",
                        Set.of("<span class=\"highlighted\">configuration</span>")),
                Arguments.of("config option",
                        Set.of("<span class=\"highlighted\">configuration options</span>")),
                Arguments.of("jpa",
                        Set.of("<span class=\"highlighted\">Jakarta Persistence</span>")));
    }

    @ParameterizedTest
    @MethodSource
    void synonymsContent(String query, Set<String> expectedContentHighlights) {
        var hits = searchHitSearchResult(query).hits();
        assertThat(expectedContentHighlights)
                .allSatisfy(expectedContentHighlight -> {
                    assertThat(hits)
                            .flatExtracting(GuideSearchHit::content)
                            .anySatisfy(hitTitle -> assertThat(hitTitle).containsIgnoringCase(expectedContentHighlight));
                });
    }

    private List<? extends Arguments> synonymsContent() {
        return List.of(
                Arguments.of("Development Service",
                        Set.of("<span class=\"highlighted\">Dev Services</span>",
                                "<span class=\"highlighted\">dev-service</span>-amqp")),
                Arguments.of("dev Service",
                        Set.of("<span class=\"highlighted\">Dev Services</span>",
                                "<span class=\"highlighted\">dev-service</span>-amqp")),
                Arguments.of("rest easy",
                        Set.of("<span class=\"highlighted\">REST</span>", "<span class=\"highlighted\">RESTEasy</span>")),
                Arguments.of("vertx",
                        Set.of("<span class=\"highlighted\">io.vertx.core.Vertx</span>",
                                "<span class=\"highlighted\">Vert.x</span>", "<span class=\"highlighted\">vertx</span>")),
                Arguments.of("rest api",
                        Set.of("<span class=\"highlighted\">REST</span>", "<span class=\"highlighted\">RESTEasy</span>")));
    }

    private static SearchResult<GuideSearchHit> searchHitSearchResult(String q) {
        return given()
                .queryParam("q", q)
                // Bumping the number of snippets to give low-score matching terms more chance to appear in highlights.
                // This is fine because these tests are not about relevance,
                // just about checking that synonyms are detected correctly.
                .queryParam("contentSnippets", 10)
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
    }
}
