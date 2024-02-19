package io.quarkus.search.app.indexing;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import io.quarkus.search.app.SearchService;
import io.quarkus.search.app.dto.GuideSearchHit;
import io.quarkus.search.app.dto.SearchResult;
import io.quarkus.search.app.testsupport.QuarkusIOSample;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.awaitility.Awaitility;

import io.restassured.common.mapper.TypeRef;

@QuarkusTest
@TestProfile(SchedulerTest.Profile.class)
@TestHTTPEndpoint(SearchService.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusIOSample.Setup(filter = QuarkusIOSample.SearchServiceFilterDefinition.class)
class SchedulerTest {
    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("indexing.on-startup.when", "never",
                    // Every 10 seconds starting at second 00, every minute starting at minute :00, of every hour:
                    "indexing.scheduled.cron", "0/10 0/1 * ? * * *");
        }
    }

    private static final TypeRef<SearchResult<GuideSearchHit>> SEARCH_RESULT_SEARCH_HITS = new TypeRef<>() {
    };
    private static final String GUIDES_SEARCH = "/guides/search";

    @Test
    void scheduler() {
        // since we've disabled the index-on-start there should be no indexes until the scheduler kicks in:
        Awaitility.await().timeout(Duration.ofMinutes(1)).pollDelay(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(given()
                    .when().get(GUIDES_SEARCH)
                    .then()
                    .statusCode(200)
                    .extract().body().as(SEARCH_RESULT_SEARCH_HITS)
                    .total().lowerBound()).isPositive();
        });
    }

}
