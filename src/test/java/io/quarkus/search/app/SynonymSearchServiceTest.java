package io.quarkus.search.app;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import io.quarkus.search.app.dto.GuideSearchHit;
import io.quarkus.search.app.dto.SearchResult;
import io.quarkus.search.app.testsupport.QuarkusIOSample;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.awaitility.Awaitility;

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

    protected int managementPort() {
        if (getClass().getName().endsWith("IT")) {
            return 9000;
        } else {
            return 9001;
        }
    }

    @BeforeAll
    void waitForIndexing() {
        Awaitility.await().timeout(Duration.ofMinutes(1))
                .untilAsserted(() -> when().get("http://localhost:" + managementPort() + "/q/health/ready")
                        .then()
                        .statusCode(200));
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.BODY);
    }

    @ParameterizedTest
    @MethodSource
    void synonymsTitle(String query, String result) {
        assertThat(searchHitSearchResult(query).hits()).extracting(GuideSearchHit::title)
                .contains(result);
    }

    private List<? extends Arguments> synonymsTitle() {
        return List.of(
                Arguments.of("REST Development Service",
                        "<span class=\"highlighted\">Dev</span> <span class=\"highlighted\">Services</span> Overview"),
                Arguments.of("rest easy",
                        "Writing <span class=\"highlighted\">REST</span> Services with <span class=\"highlighted\">RESTEasy</span> Reactive"),
                Arguments.of("vertx",
                        "<span class=\"highlighted\">Vert.x</span> Reference Guide"),
                Arguments.of("rest api",
                        "Writing <span class=\"highlighted\">REST</span> Services with <span class=\"highlighted\">RESTEasy</span> Reactive"));
    }

    @ParameterizedTest
    @MethodSource
    void synonymsContent(String query, Set<String> result) {
        assertThat(searchHitSearchResult(query).hits()).extracting(GuideSearchHit::content)
                .contains(result);
    }

    private List<? extends Arguments> synonymsContent() {
        return List.of(
                Arguments.of("Development Service",
                        Set.of("We refer to this capability as <span class=\"highlighted\">Dev</span> <span class=\"highlighted\">Services</span>.",
                                "In this case, before starting a container, <span class=\"highlighted\">Dev</span> <span class=\"highlighted\">Services</span> for AMQP looks for a container with the quarkus-<span class=\"highlighted\">dev</span>-<span class=\"highlighted\">service</span>-amqp")),
                Arguments.of("dev Service",
                        Set.of("We refer to this capability as <span class=\"highlighted\">Dev</span> <span class=\"highlighted\">Services</span>.",
                                "In this case, before starting a container, <span class=\"highlighted\">Dev</span> <span class=\"highlighted\">Services</span> for AMQP looks for a container with the quarkus-<span class=\"highlighted\">dev</span>-<span class=\"highlighted\">service</span>-amqp")),
                Arguments.of("rest easy",
                        Set.of("Writing <span class=\"highlighted\">REST</span> Services with <span class=\"highlighted\">RESTEasy</span> Reactive This guide explains how to write <span class=\"highlighted\">REST</span> Services with <span class=\"highlighted\">RESTEasy</span>",
                                "Reactive and <span class=\"highlighted\">REST</span> Client Reactive interactions In Quarkus, the <span class=\"highlighted\">RESTEasy</span> Reactive extension and the <span class=\"highlighted\">REST</span>")),
                Arguments.of("vertx",
                        Set.of("Use codecs The https:&#x2F;&#x2F;vertx.io&#x2F;docs&#x2F;<span class=\"highlighted\">vertx</span>-core&#x2F;java&#x2F;event_bus[<span class=\"highlighted\">Vert.x</span> Event",
                                "Access the <span class=\"highlighted\">Vert.x</span> instance To access the managed <span class=\"highlighted\">Vert.x</span> instance, add the quarkus-<span class=\"highlighted\">vertx</span> extension to")),
                Arguments.of("rest api",
                        Set.of("Writing <span class=\"highlighted\">REST</span> Services with <span class=\"highlighted\">RESTEasy</span> Reactive This guide explains how to write <span class=\"highlighted\">REST</span> Services with <span class=\"highlighted\">RESTEasy</span>",
                                "Reactive and <span class=\"highlighted\">REST</span> Client Reactive interactions In Quarkus, the <span class=\"highlighted\">RESTEasy</span> Reactive extension and the <span class=\"highlighted\">REST</span>")));
    }

    private static SearchResult<GuideSearchHit> searchHitSearchResult(String q) {
        return given()
                .queryParam("q", q)
                .queryParam("contentSnippets", 2)
                .when().get(GUIDES_SEARCH)
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
    }
}
