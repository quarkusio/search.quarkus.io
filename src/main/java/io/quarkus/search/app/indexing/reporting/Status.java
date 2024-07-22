package io.quarkus.search.app.indexing.reporting;

public enum Status {
    IN_PROGRESS,
    SUCCESS,
    WARNING,
    /**
     * Indexing failed, but a retry has been scheduled.
     */
    UNSTABLE,
    CRITICAL
}
