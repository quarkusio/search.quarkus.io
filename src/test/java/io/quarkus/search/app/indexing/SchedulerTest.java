package io.quarkus.search.app.indexing;

import java.util.Map;

import io.quarkus.search.app.SearchService;
import io.quarkus.search.app.testsupport.QuarkusIOSample;
import io.quarkus.search.app.testsupport.SetupUtil;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

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

    @Test
    void scheduler() {
        // since we've disabled the index-on-start there should be no indexes until the scheduler kicks in:
        SetupUtil.waitForIndexing(getClass());
    }

}
