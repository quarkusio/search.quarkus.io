package io.quarkus.search.app.indexing.reporting;

public enum Status {
    SUCCESS,
    WARNING,
    /**
     * Indexing failed, but a retry has been scheduled.
     */
    UNSTABLE,
    CRITICAL
}
