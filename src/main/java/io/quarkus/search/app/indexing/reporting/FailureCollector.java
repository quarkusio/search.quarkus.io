package io.quarkus.search.app.indexing.reporting;

public interface FailureCollector {

    enum Level {
        CRITICAL,
        WARNING;
    }

    enum Stage {
        PARSING,
        TRANSLATION,
        INDEXING;
    }

    void warning(Stage stage, String details);

    void warning(Stage stage, String details, Exception exception);

    void critical(Stage stage, String details);

    void critical(Stage stage, String details, Exception exception);

}
