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
                        "Writing <span class=\"highlighted\">REST</span> Services with <span class=\"highlighted\">RESTEasy</span> Reactive"),
                Arguments.of("config",
                        "All <span class=\"highlighted\">configuration</span> options"),
                Arguments.of("config option",
                        "All <span class=\"highlighted\">configuration</span> <span class=\"highlighted\">options</span>"),
                Arguments.of("jpa",
                        "Using Hibernate ORM and <span class=\"highlighted\">Jakarta</span> <span class=\"highlighted\">Persistence</span>"));
    }

    @ParameterizedTest
    @MethodSource
    void synonymsContent(String query, Set<String> result) {
        assertThat(searchHitSearchResult(query).hits()).flatExtracting(GuideSearchHit::content)
                .containsAll(result);
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
                        Set.of("}\n\n} You can inject either the: <span class=\"highlighted\">io.vertx.core.Vertx</span> instance exposing the bare <span class=\"highlighted\">Vert.x</span> API <span class=\"highlighted\">io.vertx.mutiny.core.Vertx</span>",
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
