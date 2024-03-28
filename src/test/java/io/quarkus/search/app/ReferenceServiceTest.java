package io.quarkus.search.app;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.quarkus.search.app.testsupport.QuarkusIOSample;
import io.quarkus.search.app.testsupport.SetupUtil;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.filter.log.LogDetail;

@QuarkusTest
@TestHTTPEndpoint(SearchService.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusIOSample.Setup
class ReferenceServiceTest {
    private static final TypeRef<List<String>> LIST_OF_STRINGS = new TypeRef<>() {
    };

    private List<String> get(String referenceName) {
        return when().get("/" + referenceName)
                .then()
                .statusCode(200)
                .extract().body().as(LIST_OF_STRINGS);
    }

    @BeforeAll
    void setup() {
        SetupUtil.waitForIndexing(getClass());
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.BODY);
    }

    @Test
    void versions() {
        assertThat(get("versions")).containsExactly("main", "latest", "3.2");
    }

    @Test
    void languages() {
        assertThat(get("languages")).containsExactly("en", "es", "pt", "cn", "ja");
    }

    @Test
    void categories() {
        assertThat(get("categories")).containsExactly(
                "alt-languages",
                "architecture",
                "cloud",
                "compatibility",
                "core",
                "data",
                "miscellaneous",
                "security",
                "web",
                "writing-extensions");
    }
}
