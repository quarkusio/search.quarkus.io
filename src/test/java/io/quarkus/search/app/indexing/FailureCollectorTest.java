package io.quarkus.search.app.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FailureCollectorTest {

    private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @ParameterizedTest
    @CsvSource(textBlock = """
            [PROD] Status report of quarkus.io content indexing: Critical (updated 1970-01-01T00:00:00Z),\
            [PROD] Status report of quarkus.io content indexing,Critical
            [PROD] Status report of quarkus.io content indexing: Warning (updated 1970-01-01T00:00:00Z),\
            [PROD] Status report of quarkus.io content indexing,Warning
            [PROD] Status report of quarkus.io content indexing: Success (updated 1970-01-01T00:00:00Z),\
            [PROD] Status report of quarkus.io content indexing,Success
            [PROD] search.quarkus.io indexing status: Critical (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status (updated 2024-05-28T00:03:23Z),Critical
            [PROD] search.quarkus.io indexing status: Warning (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status (updated 2024-05-28T00:03:23Z),Warning
            [PROD] search.quarkus.io indexing status: Success (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status (updated 2024-05-28T00:03:23Z),Success
            [PROD] search.quarkus.io indexing status: Critical (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status: Critical (updated 2024-05-28T00:03:23Z),Critical
            [PROD] search.quarkus.io indexing status: Warning (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status: Critical (updated 2024-05-28T00:03:23Z),Warning
            [PROD] search.quarkus.io indexing status: Success (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status: Critical (updated 2024-05-28T00:03:23Z),Success
            [PROD] search.quarkus.io indexing status: Critical (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status: Warning (updated 2024-05-28T00:03:23Z),Critical
            [PROD] search.quarkus.io indexing status: Warning (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status: Warning (updated 2024-05-28T00:03:23Z),Warning
            [PROD] search.quarkus.io indexing status: Success (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status: Warning (updated 2024-05-28T00:03:23Z),Success
            [PROD] search.quarkus.io indexing status: Critical (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status: Success (updated 2024-05-28T00:03:23Z),Critical
            [PROD] search.quarkus.io indexing status: Warning (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status: Success (updated 2024-05-28T00:03:23Z),Warning
            [PROD] search.quarkus.io indexing status: Success (updated 1970-01-01T00:00:00Z),\
            [PROD] search.quarkus.io indexing status: Success (updated 2024-05-28T00:03:23Z),Success
            """)
    void insertStatusAndUpdateDate(String expected, String currentTitle, String status) {
        assertThat(FailureCollector.GithubFailureReporter.insertStatusAndUpdateDate(clock, currentTitle, status))
                .isEqualTo(expected);
    }

}