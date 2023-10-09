package io.quarkus.search.app.fetching;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.file.PathUtils;

final class FetchedDirectory implements Closeable {
    public static FetchedDirectory temp() throws IOException {
        return new FetchedDirectory(Files.createTempDirectory("search-quarkus-io-fetch"), true);
    }

    public static FetchedDirectory of(Path path) {
        return new FetchedDirectory(path, false);
    }

    private final Path path;
    private boolean shouldDelete;

    private FetchedDirectory(Path path, boolean shouldDelete) {
        this.path = path;
        this.shouldDelete = shouldDelete;
    }

    @Override
    public void close() throws IOException {
        if (shouldDelete) {
            PathUtils.deleteDirectory(path);
            shouldDelete = false;
        }
    }

    public Path path() {
        return path;
    }

}
