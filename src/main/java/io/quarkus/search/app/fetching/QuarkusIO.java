package io.quarkus.search.app.fetching;

import static io.quarkus.search.app.util.UncheckedIOFunction.uncheckedIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;

import io.quarkus.search.app.QuarkusVersions;
import io.quarkus.search.app.asciidoc.Asciidoc;
import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.hibernate.PathWrapper;

public class QuarkusIO implements AutoCloseable {

    private final FetchedDirectory directory;

    QuarkusIO(FetchedDirectory directory) {
        this.directory = directory;
    }

    @Override
    public void close() throws Exception {
        directory.close();
    }

    @SuppressWarnings("resource")
    public Stream<Guide> guides() throws IOException {
        return guideDirectories()
                .flatMap(uncheckedIO(guidesDirectory -> Files.list(guidesDirectory.path)
                        .filter(path -> {
                            String filename = path.getFileName().toString();
                            return !filename.startsWith("_") && FilenameUtils.isExtension(filename, "adoc");
                        })
                        .map(path -> parseGuide(guidesDirectory, path))));
    }

    @SuppressWarnings("resource")
    private Stream<GuidesDirectory> guideDirectories() throws IOException {
        return Stream.concat(
                Stream.of(new GuidesDirectory(directory.path().resolve("_guides"),
                        QuarkusVersions.LATEST, "/guides/")),
                Files.list(directory.path().resolve("_versions"))
                        .map(p -> {
                            var version = p.getFileName().toString();
                            return new GuidesDirectory(p.resolve("guides"), version,
                                    "/version/" + version + "/guides/");
                        }));
    }

    record GuidesDirectory(Path path, String version, String htmlPathPrefix) {
    }

    private Guide parseGuide(GuidesDirectory guidesDirectory, Path path) {
        var guide = new Guide();
        guide.version = guidesDirectory.version;
        guide.relativePath = guidesDirectory.htmlPathPrefix
                + FilenameUtils.removeExtension(guidesDirectory.path.relativize(path).toString());
        guide.fullContentPath = new PathWrapper(path);
        Asciidoc.parse(path, title -> guide.title = title,
                Map.of("summary", summary -> guide.summary = summary,
                        "keywords", keywords -> guide.keywords = keywords,
                        "categories", categories -> guide.categories = toSet(categories),
                        "topics", topics -> guide.topics = toSet(topics),
                        "extensions", extensions -> guide.extensions = toSet(extensions)));
        return guide;
    }

    private static Set<String> toSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
