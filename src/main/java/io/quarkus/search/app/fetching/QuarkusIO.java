package io.quarkus.search.app.fetching;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;

import io.quarkus.search.app.asciidoc.Asciidoc;
import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.hibernate.PathWrapper;

public class QuarkusIO implements AutoCloseable {

    private final FetchedDirectory directory;

    public QuarkusIO(FetchedDirectory directory) {
        this.directory = directory;
    }

    @Override
    public void close() throws Exception {
        directory.close();
    }

    public Stream<Guide> guides() throws IOException {
        return Files.list(directory.path().resolve("_guides"))
                .filter(path -> FilenameUtils.isExtension(path.getFileName().toString(), "adoc"))
                .map(this::parseGuide);
    }

    private Guide parseGuide(Path path) {
        var guide = new Guide();
        guide.relativePath = "/guides/" + FilenameUtils.getBaseName(path.getFileName().toString());
        guide.fullContentPath = new PathWrapper(path);
        Asciidoc.parse(path, title -> guide.title = title,
                Map.of("summary", summary -> guide.summary = summary,
                        "keywords", keywords -> guide.keywords = keywords));
        return guide;
    }

}
