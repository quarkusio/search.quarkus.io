package io.quarkus.search.app.indexing.state;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quarkus.search.app.indexing.reporting.Failure;
import io.quarkus.search.app.indexing.reporting.FailureCollector;
import io.quarkus.search.app.indexing.reporting.Status;
import io.quarkus.search.app.indexing.reporting.StatusReporter;

import io.quarkus.logging.Log;

public class IndexingState {

    private final StatusReporter reporter;

    private final AtomicBoolean inProgress = new AtomicBoolean();

    public IndexingState(StatusReporter reporter) {
        this.reporter = reporter;
    }

    public boolean isInProgress() {
        return inProgress.get();
    }

    public Attempt tryStart() {
        // Indexing requires exclusive access to the DB/indexes
        if (!inProgress.compareAndSet(false, true)) {
            throw new IndexingAlreadyInProgressException();
        }
        return new Attempt();
    }

    public class Attempt implements Closeable, FailureCollector {

        private final EnumMap<Level, List<Failure>> failures = new EnumMap<>(Level.class);

        private Attempt() {
            for (Level value : Level.values()) {
                failures.put(value, Collections.synchronizedList(new ArrayList<>()));
            }
        }

        @Override
        public void close() {
            try {
                Status status = indexingResultStatus(failures);
                reporter.report(status, failures);
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
