package io.quarkus.search.app.indexing.state;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.quarkus.search.app.indexing.reporting.Failure;
import io.quarkus.search.app.indexing.reporting.FailureCollector;
import io.quarkus.search.app.indexing.reporting.Status;
import io.quarkus.search.app.indexing.reporting.StatusReporter;

import io.quarkus.logging.Log;

import io.smallrye.mutiny.subscription.Cancellable;

public class IndexingState {

    private final StatusReporter reporter;
    private final RetryConfig retryConfig;
    private final Function<Duration, Cancellable> retryScheduler;

    private final AtomicBoolean inProgress = new AtomicBoolean();
    private final AtomicInteger attempts = new AtomicInteger();
    private volatile Cancellable scheduledRetry;

    public IndexingState(StatusReporter reporter, RetryConfig retryConfig,
            Function<Duration, Cancellable> retryScheduler) {
        this.reporter = reporter;
        this.retryConfig = retryConfig;
        this.retryScheduler = retryScheduler;
    }

    public boolean isInProgress() {
        return inProgress.get();
    }

    public Attempt tryStart(boolean allowRetry) {
        // Indexing requires exclusive access to the DB/indexes
        if (!inProgress.compareAndSet(false, true)) {
            throw new IndexingAlreadyInProgressException();
        }
        if (scheduledRetry != null) {
            scheduledRetry.cancel();
            scheduledRetry = null;
        }
        reporter.report(Status.IN_PROGRESS, Map.of());
        return new Attempt(allowRetry);
    }

    public class Attempt implements Closeable, FailureCollector {

        private final boolean allowRetry;
        private final EnumMap<Level, List<Failure>> failures = new EnumMap<>(Level.class);

        private Attempt(boolean allowRetry) {
            this.allowRetry = allowRetry;
            for (Level value : Level.values()) {
                failures.put(value, Collections.synchronizedList(new ArrayList<>()));
            }
        }

        @Override
        public void close() {
            try {
                Status status = indexingResultStatus(failures);
                switch (status) {
                    case SUCCESS, WARNING -> {
                        attempts.set(0);
                        reporter.report(status, failures);
                    }
                    case CRITICAL -> {
                        if (scheduleRetry()) {
                            reporter.report(Status.UNSTABLE, failures);
                        } else {
                            reporter.report(Status.CRITICAL, failures);
                        }
                    }
                }
            } finally {
                inProgress.set(false);
            }
        }

        @Override
        public void warning(Stage stage, String details) {
            warning(stage, details, null);
        }

        @Override
        public void warning(Stage stage, String details, Exception exception) {
            Log.warn(details, exception);
            failures.get(Level.WARNING).add(new Failure(Level.WARNING, stage, details, exception));
        }

        @Override
        public void critical(Stage stage, String details) {
            critical(stage, details, null);
        }

        @Override
        public void critical(Stage stage, String details, Exception exception) {
            Log.error(details, exception);
            failures.get(Level.CRITICAL).add(new Failure(Level.CRITICAL, stage, details, exception));
        }

        private boolean scheduleRetry() {
            if (!allowRetry) {
                return false;
            }
            if (attempts.incrementAndGet() < retryConfig.maxAttempts()) {
                try {
                    scheduledRetry = retryScheduler.apply(retryConfig.delay());
                    // If we get here, a retry was scheduled.
                    warning(Stage.INDEXING, "Indexing will be tried again later.");
                    return true;
                } catch (RuntimeException e) {
                    // If we get here, we'll abort.
                    critical(Stage.INDEXING, "Failed to schedule retry: " + e.getMessage(),
                            e);
                    return false;
                }
            } else {
                critical(Stage.INDEXING, "Tried %s time(s), aborting".formatted(retryConfig.maxAttempts()));
                return false;
            }
        }

        private static Status indexingResultStatus(Map<Level, List<Failure>> failures) {
            if (failures.get(Level.CRITICAL).isEmpty()) {
                if (failures.get(Level.WARNING).isEmpty()) {
                    return Status.SUCCESS;
                } else {
                    return Status.WARNING;
                }
            } else {
                return Status.CRITICAL;
            }
        }

    }

}
