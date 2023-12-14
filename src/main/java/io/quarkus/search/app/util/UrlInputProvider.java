package io.quarkus.search.app.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkus.search.app.hibernate.InputProvider;
import io.quarkus.search.app.indexing.FailureCollector;

import io.quarkus.logging.Log;

public class UrlInputProvider implements InputProvider {

    private static final byte[] NO_CONTENT_CONTENT = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Quarkus</title></head><body><!--No Content--></body></html>"
            .getBytes(StandardCharsets.UTF_8);

    private final Path temporaryFile;
    private final URL url;

    public UrlInputProvider(CloseableDirectory directory, URI uri, FailureCollector failureCollector) {
        try {
            this.url = uri.toURL();
            // We are creating another tmp directory just to be safe in case multiple versions are targeting the same
            // external guide or the same guide is referenced multiple times within the same file:
            Path temporaryFile = Files.createTempDirectory(directory.path(), "quarkiverse-")
                    .resolve(this.url.getPath().replaceAll("[./]", "-"));
            try (InputStream inputStream = this.url.openStream()) {
                Files.copy(inputStream, temporaryFile);
            } catch (IOException e) {
                String message = "Failed to prefetch the guide content from the URL (%s): %s".formatted(this.url,
                        e.getMessage());
                Log.warn(message, e);
                failureCollector.warning(FailureCollector.Stage.PARSING, message);
                temporaryFile = null;
            }
            this.temporaryFile = temporaryFile;

        } catch (MalformedURLException e) {
            String message = "Unable to create an URL from: %s : %s".formatted(uri, e.getMessage());
            failureCollector.critical(FailureCollector.Stage.PARSING, message);
            throw new IllegalArgumentException(message, e);
        } catch (IOException e) {
            String message = "Unable to create a temporary copy of the content from: %s : %s".formatted(uri, e.getMessage());
            failureCollector.critical(FailureCollector.Stage.PARSING, message);
            throw new IllegalArgumentException(message, e);
        }
    }

    @Override
    public InputStream open() throws IOException {
        return temporaryFile != null ? Files.newInputStream(temporaryFile)
                : new ByteArrayInputStream(NO_CONTENT_CONTENT);
    }

    @Override
    public String toString() {
        return "UrlInputProvider{" +
                "temporaryFile=" + temporaryFile +
                ", url=" + url +
                '}';
    }
}
