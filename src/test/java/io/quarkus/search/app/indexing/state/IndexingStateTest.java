package io.quarkus.search.app.indexing.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.function.Function;

import io.quarkus.search.app.indexing.reporting.FailureCollector.Stage;
import io.quarkus.search.app.indexing.reporting.Status;
import io.quarkus.search.app.indexing.reporting.StatusReporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.subscription.Cancellable;

@ExtendWith(MockitoExtension.class)
public class IndexingStateTest {

    @Mock
    StatusReporter statusReporterMock;

    @Mock
    Function<Duration, Cancellable> retrySchedulerMock;

    @Test
    void success() {
        var state = new IndexingState(statusReporterMock,
                new ExplicitRetryConfig(3, Duration.ofMinutes(2)), retrySchedulerMock);
        assertThat(state.isInProgress()).isFalse();

        try (IndexingState.Attempt attempt = state.tryStart(true)) {
            assertThat(state.isInProgress()).isTrue();

            // No warning/critical: success
        }
        verify(statusReporterMock).report(eq(Status.SUCCESS), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

    @Test
    void concurrent() {
        var state = new IndexingState(statusReporterMock,
                new ExplicitRetryConfig(3, Duration.ofMinutes(2)), retrySchedulerMock);
        assertThat(state.isInProgress()).isFalse();

        try (IndexingState.Attempt attempt = state.tryStart(true)) {
            assertThat(state.isInProgress()).isTrue();

            // Try concurrent indexing...
            assertThatThrownBy(() -> state.tryStart(true))
                    .isInstanceOf(IndexingAlreadyInProgressException.class);
        }
        verify(statusReporterMock).report(eq(Status.SUCCESS), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

    @Test
    void warning() {
        var state = new IndexingState(statusReporterMock,
                new ExplicitRetryConfig(3, Duration.ofMinutes(2)), retrySchedulerMock);
        assertThat(state.isInProgress()).isFalse();

        try (IndexingState.Attempt attempt = state.tryStart(true)) {
            assertThat(state.isInProgress()).isTrue();

            attempt.warning(Stage.INDEXING, "Some warning");
        }
        verify(statusReporterMock).report(eq(Status.WARNING), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

    @Test
    void failure_max1Attempt() {
        var state = new IndexingState(statusReporterMock,
                new ExplicitRetryConfig(1, Duration.ofMinutes(2)), retrySchedulerMock);
        assertThat(state.isInProgress()).isFalse();

        try (IndexingState.Attempt attempt = state.tryStart(true)) {
            assertThat(state.isInProgress()).isTrue();

            attempt.critical(Stage.INDEXING, "Something critical");
        }
        verify(statusReporterMock).report(eq(Status.CRITICAL), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

    @Test
    void failure_retryNotAllowed() {
        var state = new IndexingState(statusReporterMock,
                new ExplicitRetryConfig(3, Duration.ofMinutes(2)), retrySchedulerMock);
        assertThat(state.isInProgress()).isFalse();

        try (IndexingState.Attempt attempt = state.tryStart(false)) {
            assertThat(state.isInProgress()).isTrue();

            attempt.critical(Stage.INDEXING, "Something critical");
        }
        verify(statusReporterMock).report(eq(Status.CRITICAL), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

    @Test
    void failure_retry_then_success() {
        var retryDelay = Duration.ofMinutes(2);
        var state = new IndexingState(statusReporterMock,
                new ExplicitRetryConfig(3, retryDelay), retrySchedulerMock);
        assertThat(state.isInProgress()).isFalse();

        var cancellableMock = mock(Cancellable.class);
        when(retrySchedulerMock.apply(any())).thenReturn(cancellableMock);

        try (IndexingState.Attempt attempt = state.tryStart(true)) {
            assertThat(state.isInProgress()).isTrue();

            attempt.critical(Stage.INDEXING, "Something critical");
        }
        verify(statusReporterMock).report(eq(Status.UNSTABLE), anyMap());
        verify(retrySchedulerMock).apply(retryDelay);
        assertThat(state.isInProgress()).isFalse();

        reset(statusReporterMock, retrySchedulerMock);

        try (IndexingState.Attempt attempt = state.tryStart(true)) {
            verify(cancellableMock).cancel();
            assertThat(state.isInProgress()).isTrue();

            attempt.critical(Stage.INDEXING, "Something critical");
        }
        verify(statusReporterMock).report(eq(Status.UNSTABLE), anyMap());
        verify(retrySchedulerMock).apply(retryDelay);
        assertThat(state.isInProgress()).isFalse();

        reset(statusReporterMock, retrySchedulerMock);

        try (IndexingState.Attempt attempt = state.tryStart(true)) {
            verify(cancellableMock).cancel();
            assertThat(state.isInProgress()).isTrue();

            // No warning/critical: success
        }
        verify(statusReporterMock).report(eq(Status.SUCCESS), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

    @Test
    void failure_retry_then_maxAttempts() {
        var retryDelay = Duration.ofMinutes(2);
        var state = new IndexingState(statusReporterMock,
                new ExplicitRetryConfig(3, retryDelay), retrySchedulerMock);
        assertThat(state.isInProgress()).isFalse();

        var cancellableMock = mock(Cancellable.class);
        when(retrySchedulerMock.apply(any())).thenReturn(cancellableMock);

        try (IndexingState.Attempt attempt = state.tryStart(true)) {
            assertThat(state.isInProgress()).isTrue();

            attempt.critical(Stage.INDEXING, "Something critical");
        }
        verify(statusReporterMock).report(eq(Status.UNSTABLE), anyMap());
        verify(retrySchedulerMock).apply(retryDelay);
        assertThat(state.isInProgress()).isFalse();

        reset(statusReporterMock, retrySchedulerMock);

        try (IndexingState.Attempt attempt = state.tryStart(true)) {
            verify(cancellableMock).cancel();
            assertThat(state.isInProgress()).isTrue();

            attempt.critical(Stage.INDEXING, "Something critical");
        }
        verify(statusReporterMock).report(eq(Status.UNSTABLE), anyMap());
        verify(retrySchedulerMock).apply(retryDelay);
        assertThat(state.isInProgress()).isFalse();

        reset(statusReporterMock, retrySchedulerMock);

        try (IndexingState.Attempt attempt = state.tryStart(true)) {
            verify(cancellableMock).cancel();
            assertThat(state.isInProgress()).isTrue();

            attempt.critical(Stage.INDEXING, "Something critical");
        }
        verify(statusReporterMock).report(eq(Status.CRITICAL), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

    @Test
    void failure_retry_then_retryNotAllowed() {
        var state = new IndexingState(statusReporterMock,
                new ExplicitRetryConfig(3, Duration.ofMinutes(2)), retrySchedulerMock);
        assertThat(state.isInProgress()).isFalse();

        var cancellableMock = mock(Cancellable.class);
        when(retrySchedulerMock.apply(any())).thenReturn(cancellableMock);

        try (IndexingState.Attempt attempt = state.tryStart(true)) {
            assertThat(state.isInProgress()).isTrue();

            attempt.critical(Stage.INDEXING, "Something critical");
        }
        verify(statusReporterMock).report(eq(Status.UNSTABLE), anyMap());
        assertThat(state.isInProgress()).isFalse();

        try (IndexingState.Attempt attempt = state.tryStart(false)) {
            verify(cancellableMock).cancel();
            assertThat(state.isInProgress()).isTrue();

            attempt.critical(Stage.INDEXING, "Something critical");
        }
        verify(statusReporterMock).report(eq(Status.CRITICAL), anyMap());
        assertThat(state.isInProgress()).isFalse();
    }

}
