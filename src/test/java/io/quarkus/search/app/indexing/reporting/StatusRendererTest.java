package io.quarkus.search.app.indexing.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatusRendererTest {

    private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @ParameterizedTest
    @CsvSource(textBlock = """
            [PROD] Status report of quarkus.io content indexing: Critical (updated 1970-01-01T00:00:00Z),\
            CRITICAL,[PROD] Status report of quarkus.io content indexing
            [PROD] Status report of quarkus.io content indexing: Warning (updated 1970-01-01T00:00:00Z),\
            WARNING,[PROD] Status report of quarkus.io content indexing
            [PROD] Status report of quarkus.io content indexing: Success (updated 1970-01-01T00:00:00Z),\
            SUCCESS,[PROD] Status report of quarkus.io content indexing
            [PROD] search.quarkus.io indexing status: Critical (updated 1970-01-01T00:00:00Z),\
            CRITICAL,[PROD] search.quarkus.io indexing status (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Warning (updated 1970-01-01T00:00:00Z),\
            WARNING,[PROD] search.quarkus.io indexing status (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Success (updated 1970-01-01T00:00:00Z),\
            SUCCESS,[PROD] search.quarkus.io indexing status (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Critical (updated 1970-01-01T00:00:00Z),\
            CRITICAL,[PROD] search.quarkus.io indexing status: Critical (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Warning (updated 1970-01-01T00:00:00Z),\
            WARNING,[PROD] search.quarkus.io indexing status: Critical (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Success (updated 1970-01-01T00:00:00Z),\
            SUCCESS,[PROD] search.quarkus.io indexing status: Critical (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Critical (updated 1970-01-01T00:00:00Z),\
            CRITICAL,[PROD] search.quarkus.io indexing status: Warning (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Warning (updated 1970-01-01T00:00:00Z),\
            WARNING,[PROD] search.quarkus.io indexing status: Warning (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Success (updated 1970-01-01T00:00:00Z),\
            SUCCESS,[PROD] search.quarkus.io indexing status: Warning (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Critical (updated 1970-01-01T00:00:00Z),\
            CRITICAL,[PROD] search.quarkus.io indexing status: Success (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Warning (updated 1970-01-01T00:00:00Z),\
            WARNING,[PROD] search.quarkus.io indexing status: Success (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Success (updated 1970-01-01T00:00:00Z),\
            SUCCESS,[PROD] search.quarkus.io indexing status: Success (updated 2024-05-28T00:03:23Z)
            [PROD] search.quarkus.io indexing status: Success (updated 1970-01-01T00:00:00Z),\
            SUCCESS,[PROD] search.quarkus.io indexing status: In progress (updated 2024-05-28T00:03:23Z)
            """)
    void toStatusSummary(String expected, Status status, String currentTitle) {
        assertThat(StatusRenderer.toStatusSummary(clock, status, currentTitle))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            'Original description
            <!-- Automatic message start -->
            ## Latest indexing report
            > Automatic message
            > and some more
            <!-- Automatic message end -->',\
            'Original description',\
            'Automatic message
            and some more'
            'Original description
            <!-- Automatic message start -->
            ## Latest indexing report
            > New automatic message
            > and some more
            <!-- Automatic message end -->',\
            'Original description
            <!-- Automatic message start -->
            Random garbage
            <!-- Automatic message end -->',\
            'New automatic message
            and some more'
            """)
    void insertMessageInIssueDescription(String expected, String originalIssueDescription, String newMessage) {
        assertThat(StatusRenderer.insertMessageInIssueDescription(originalIssueDescription, newMessage))
                .isEqualTo(expected);
    }

}
