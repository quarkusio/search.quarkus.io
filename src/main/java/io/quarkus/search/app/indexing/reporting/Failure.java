package io.quarkus.search.app.indexing.reporting;

import java.util.Comparator;

/**
 * @param level Whether the reported failure should lead to reporting the indexing as incomplete/failed or not.
 * @param stage Where the failure happened.
 * @param details Failure details.
 * @param exception An exception that has caused the failure.
 */
public record Failure(FailureCollector.Level level, FailureCollector.Stage stage, String details, Exception exception) {

    static Comparator<Failure> COMPARATOR = Comparator.comparing(Failure::level)
            .thenComparing(Failure::stage)
            .thenComparing(Failure::details)
            // Not perfect, but then how likely it is to get
            // two failures with everything identical except the exception?
            .thenComparing(f -> System.identityHashCode(f.exception()));

}
