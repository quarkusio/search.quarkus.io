package io.quarkus.search.app.indexing.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.quarkus.search.app.indexing.reporting.FailureCollector;
import io.quarkus.search.app.indexing.reporting.Status;
import io.quarkus.search.app.indexing.reporting.StatusReporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IndexingStateTest {

    @Mock
    StatusReporter statusReporterMock;

    @Test
    void success() {
        var state = new IndexingState(statusReporterMock);
        assertThat(state.isInProgress()).isFalse();

        try (IndexingState.Attempt attempt = state.tryStart()) {
            assertThat(state.isInProgress()).isTrue();

            // No warning/critical: success
        }
        verify(statusReporterMock).report(eq(Status.SUCCESS), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

    @Test
    void concurrent() {
        var state = new IndexingState(statusReporterMock);
        assertThat(state.isInProgress()).isFalse();

        try (IndexingState.Attempt attempt = state.tryStart()) {
            assertThat(state.isInProgress()).isTrue();

            // Try concurrent indexing...
            assertThatThrownBy(() -> state.tryStart())
                    .isInstanceOf(IndexingAlreadyInProgressException.class);
        }
        verify(statusReporterMock).report(eq(Status.SUCCESS), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

    @Test
    void warning() {
        var state = new IndexingState(statusReporterMock);
        assertThat(state.isInProgress()).isFalse();

        try (IndexingState.Attempt attempt = state.tryStart()) {
            assertThat(state.isInProgress()).isTrue();

            attempt.warning(FailureCollector.Stage.INDEXING, "Some warning");
        }
        verify(statusReporterMock).report(eq(Status.WARNING), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

    @Test
    void failure() {
        var state = new IndexingState(statusReporterMock);
        assertThat(state.isInProgress()).isFalse();

        try (IndexingState.Attempt attempt = state.tryStart()) {
            assertThat(state.isInProgress()).isTrue();

            attempt.critical(FailureCollector.Stage.INDEXING, "Something critical");
        }
        verify(statusReporterMock).report(eq(Status.CRITICAL), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

}
