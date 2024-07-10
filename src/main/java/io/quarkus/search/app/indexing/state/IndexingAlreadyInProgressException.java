package io.quarkus.search.app.indexing.state;

public class IndexingAlreadyInProgressException extends RuntimeException {
    IndexingAlreadyInProgressException() {
        super("Indexing is already in progress and cannot be started at this moment");
    }
}
