package io.quarkus.search.app.quarkiverseio;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.hibernate.InputProvider;
import io.quarkus.search.app.indexing.reporting.FailureCollector;
import io.quarkus.search.app.util.CloseableDirectory;

import io.quarkus.logging.Log;

import org.hibernate.search.util.common.impl.Closer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class QuarkiverseIO implements Closeable {

    // Quarkiverse extensions may use different directories for their "current" version.
    // We will be going one, by one from the "most likely present" directory:
    private static final List<String> LATEST_VERSIONS = List.of("dev", "main");

    public static final String QUARKIVERSE_ORIGIN = "quarkiverse-hub";

    private final FailureCollector failureCollector;

    private final Optional<Path> pages;
    private final URI baseUri;
    private final CloseableDirectory tempDir;

    public QuarkiverseIO(Optional<Path> pages, URI baseUri, FailureCollector failureCollector,
            CloseableDirectory tempDir) {
        this.failureCollector = failureCollector;
        this.pages = pages;
        this.baseUri = baseUri;
        this.tempDir = tempDir;
    }

    private Guide readGuide(Path file) {
        Guide guide = new Guide();
        guide.url = baseUri.resolve(pages.get().relativize(file).toString());
        guide.type = "reference";
        guide.origin = QUARKIVERSE_ORIGIN;

        try {
            Document document = Jsoup.parse(file);

            String title = document.select("nav.breadcrumbs li").stream()
                    .map(Element::text)
                    .collect(Collectors.joining(" | "));
            if (title.isBlank()) {
                title = document.select("h1.page").text();
            }
            if (title.isBlank()) {
                title = document.select("h3.title").text();
            }
            guide.title.set(title.trim());

            guide.summary.set(document.select("div#preamble").text());
            guide.htmlFullContentProvider.set(InputProvider.from(document, tempDir, file));
        } catch (IOException e) {
            failureCollector.warning(FailureCollector.Stage.PARSING, "Failed to parse guide file: " + file, e);
        }

        Log.debugf("Parsed guide: %s", guide.url);
        return guide;
    }

    public Stream<Guide> guides() {
        if (pages.isEmpty()) {
            return Stream.empty();
        }
        Stream<Path> quarkiverseStream = null;
        try {
            quarkiverseStream = Files.list(pages.get());
            return quarkiverseStream.filter(Files::isDirectory)
                    // First get the extensions directories:
                    .filter(dir -> dir.getFileName().toString().startsWith("quarkus"))
                    // then try to look for `dev` directory as a "latest" version,
                    // if we don't find one, we'll report it as a "warning"
                    .map(this::latestVersion)
                    .filter(Objects::nonNull)
                    .flatMap(this::filesToIndex)
                    .map(this::readGuide);
        } catch (IOException e) {
            if (quarkiverseStream != null) {
                quarkiverseStream.close();
            }
            failureCollector.critical(FailureCollector.Stage.PARSING, "Unable to fetch the Quarkiverse Docs index page.",
                    e);
            return Stream.empty();
        }
    }

    private Stream<Path> filesToIndex(Path path) {
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(
                    path, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (file.getFileName().toString().endsWith(".html")) {
                                files.add(file);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            // some extensions have these extra directories that are not "visible" from the docs page themselves,
                            // but are still deployed (and hence accessible). We want to ignore those:
                            if (dir.getFileName().toString().equals("includes")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            failureCollector.critical(FailureCollector.Stage.PARSING, "Failed to traverse the directory tree: " + path, e);
        }
        return files.stream();
    }

    private Path latestVersion(Path extensionRoot) {
        for (String maybeLatestVersion : LATEST_VERSIONS) {
            Path version = extensionRoot.resolve(maybeLatestVersion);
            if (Files.exists(version)) {
                return version;
            }
        }
        failureCollector.warning(FailureCollector.Stage.PARSING,
                "Cannot find latest version of Quarkiverse Docs within " + extensionRoot);
        return null;
    }

    @Override
    public void close() throws IOException {
        try (var closer = new Closer<IOException>()) {
            closer.push(CloseableDirectory::close, tempDir);
        }
    }
}
