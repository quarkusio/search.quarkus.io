package io.quarkus.search.app.util;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.file.PathUtils;

public final class CloseableDirectory implements Closeable {
    public static CloseableDirectory temp(String prefix) throws IOException {
        return new CloseableDirectory(Files.createTempDirectory(prefix), true);
    }

    public static CloseableDirectory of(Path path) {
        return new CloseableDirectory(path, false);
    }

    private final Path path;
    private boolean shouldDelete;

    private CloseableDirectory(Path path, boolean shouldDelete) {
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
