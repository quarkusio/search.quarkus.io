package io.quarkus.search.app.quarkusio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.search.app.entity.Guide;
import org.yaml.snakeyaml.Yaml;

class QuarkusIOMetadata {

    private final Map<String, Metadata> guides;

    private QuarkusIOMetadata(Map<String, Metadata> guides) {
        this.guides = guides;
    }

    @SuppressWarnings("unchecked")
    public static QuarkusIOMetadata parseYamlMetadata(Path quarkusYamlPath) {
        Map<String, Object> quarkusYaml;
        try (InputStream inputStream = Files.newInputStream(quarkusYamlPath)) {
            Yaml yaml = new Yaml();
            quarkusYaml = yaml.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + quarkusYamlPath, e);
        }

        List<Map<String, Object>> guides = ((Map<String, List<Object>>) quarkusYaml.get("types")).entrySet()
                .stream()
                .flatMap(e -> e.getValue().stream())
                .map(e -> (Map<String, Object>) e).toList();
        Map<String, Metadata> parsed = new HashMap<>();
        for (Map<String, Object> guide : guides) {
            Metadata metadata = new Metadata(
                    toString(guide.get("title")),
                    toString(guide.get("filename")),
                    toString(guide.get("summary")),
                    toString(guide.get("type")),
                    toString(guide.get("id")),
                    toString(guide.get("url")),
                    toString(guide.get("keywords")),
                    toSet(guide.get("categories")),
                    toSet(guide.get("topics")),
                    toSet(guide.get("extensions")));
            parsed.put(metadata.filename, metadata);
        }

        return new QuarkusIOMetadata(parsed);
    }

    public void addMetadata(Path path, Guide guide) {
        Metadata metadata = this.guides.get(path.getFileName().toString());

        guide.type = metadata.type();
        guide.title = metadata.title();
        guide.summary = metadata.summary();
        guide.categories = metadata.categories();
        guide.topics = metadata.topics();
        guide.extensions = metadata.extensions();
        guide.keywords = metadata.keywords();
    }

    record Metadata(String title, String filename, String summary, String type, String id, String url, String keywords,
            Set<String> categories, Set<String> topics, Set<String> extensions) {
    }

    private static String toString(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> toSet(Object value) {
        if (value == null) {
            return Set.of();
        }
        if (value instanceof String) {
            return toSet((String) value);
        }
        if (value instanceof Collection<?>) {
            return new HashSet<>((Collection<String>) value);
        }
        throw new IllegalArgumentException("Unknown value type to be converted to set: " + value);
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
