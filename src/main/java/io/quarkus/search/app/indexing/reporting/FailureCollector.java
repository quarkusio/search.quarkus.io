package io.quarkus.search.app.indexing.reporting;

import org.jboss.logging.Logger;

public interface FailureCollector {

    enum Level {
        /**
         * Level for failures that prevent reindexing completely.
         */
        CRITICAL(Logger.Level.ERROR),
        /**
         * Level for failures that don't prevent reindexing but cause the index to be incomplete/incorrect,
         * and may require maintainer's attention.
         */
        WARNING(Logger.Level.WARN),
        /**
         * Level for failures that don't prevent reindexing but cause the index to be incomplete/incorrect,
         * though maintainer's attention is not needed (the solution is just waiting).
         */
        INFO(Logger.Level.INFO);

        public final Logger.Level logLevel;

        Level(Logger.Level logLevel) {
            this.logLevel = logLevel;
        }
    }

    enum Stage {
        PARSING,
        TRANSLATION,
        INDEXING;
    }

    default void collect(Level level, Stage stage, String details) {
        collect(level, stage, details, null);
    }

    void collect(Level level, Stage stage, String details, Exception exception);

    default void info(Stage stage, String details) {
        collect(Level.INFO, stage, details);
    }

    default void info(Stage stage, String details, Exception exception) {
        collect(Level.INFO, stage, details, exception);
    }

    default void warning(Stage stage, String details) {
        collect(Level.WARNING, stage, details);
    }

    default void warning(Stage stage, String details, Exception exception) {
        collect(Level.WARNING, stage, details, exception);
    }

    default void critical(Stage stage, String details) {
        collect(Level.CRITICAL, stage, details);
    }

    default void critical(Stage stage, String details, Exception exception) {
        collect(Level.CRITICAL, stage, details, exception);
    }

}
