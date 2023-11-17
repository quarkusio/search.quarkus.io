package io.quarkus.search.app.quarkusio;

import static io.quarkus.search.app.quarkusio.QuarkusIO.httpUrl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.util.UrlInputProvider;
import org.yaml.snakeyaml.Yaml;

public class QuarkiverseMetadata {

    private final Collection<Guide> guides;

    private QuarkiverseMetadata(Collection<Guide> guides) {
        this.guides = guides;
    }

    @SuppressWarnings("unchecked")
    public static QuarkiverseMetadata parseYamlMetadata(URI webUri, Path quarkusYamlPath, String version) {
        return parse(quarkusYamlPath, quarkusYaml -> {
            Set<Guide> parsed = new HashSet<>();
            for (Map.Entry<String, List<Map<String, Object>>> type : ((Map<String, List<Map<String, Object>>>) quarkusYaml
                    .get("types")).entrySet()) {
                for (Map<String, Object> parsedGuide : type.getValue()) {
                    Guide guide = createGuide(webUri, version, type.getKey(), parsedGuide, "summary");
                    guide.categories = toSet(parsedGuide.get("categories"));
                    parsed.add(guide);
                }
            }

            return new QuarkiverseMetadata(parsed);
        });
    }

    @SuppressWarnings("unchecked")
    public static QuarkiverseMetadata parseYamlLegacyMetadata(URI webUri, Path quarkusYamlPath, String version) {
        return parse(quarkusYamlPath, quarkusYaml -> {
            String versionParameter = ("?quarkusDocVersion=" + version);
            Map<URI, Guide> parsed = new HashMap<>();
            for (Map<String, Object> categoryObj : ((List<Map<String, Object>>) quarkusYaml.get("categories"))) {
                String category = toString(categoryObj.get("cat-id"));
                for (Map<String, Object> parsedGuide : ((List<Map<String, Object>>) categoryObj.get("guides"))) {
                    if (toString(parsedGuide.get("url")).startsWith("http")) {
                        // we only care about external quarkiverse guides at this point ^
                        Guide guide = createGuide(webUri, version, "guide", parsedGuide, "description");
                        // since we can have the same link to a quarkiverse guide in multiple versions of quarkus,
                        // we want to somehow make them different in their ID:
                        try {
                            guide.url = new URI(guide.url.getScheme(), guide.url.getAuthority(), guide.url.getFragment(),
                                    versionParameter, guide.url.getFragment());
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                        guide.categories = Set.of(category);
                        Guide old = parsed.put(guide.url, guide);
                        if (old != null) {
                            guide.categories = combine(guide.categories, old.categories);
                        }
                        guide.categories = toSet(parsedGuide.get("categories"));
                    }
                }
            }

            return new QuarkiverseMetadata(parsed.values());
        });
    }

    private static Set<String> combine(Set<String> a, Set<String> b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        HashSet<String> result = new HashSet<>(a);
        result.addAll(b);
        return result;
    }

    private static QuarkiverseMetadata parse(Path quarkusYamlPath, Function<Map<String, Object>, QuarkiverseMetadata> parser) {
        Map<String, Object> quarkusYaml;
        try (InputStream inputStream = Files.newInputStream(quarkusYamlPath)) {
            Yaml yaml = new Yaml();
            quarkusYaml = yaml.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + quarkusYamlPath, e);
        }

        return parser.apply(quarkusYaml);
    }

    private static Guide createGuide(URI webUri, String version, String type, Map<String, Object> parsedGuide,
            String summaryKey) {
        Guide guide = new Guide();
        guide.type = type;
        guide.title = toString(parsedGuide.get("title"));
        guide.summary = toString(parsedGuide.get(summaryKey));
        guide.origin = toString(parsedGuide.get("origin"));
        guide.version = version;
        guide.url = httpUrl(webUri, version, toString(parsedGuide.get("url")));
        guide.htmlFullContentProvider = new UrlInputProvider(guide.url);
        return guide;
    }

    public Stream<Guide> createQuarkiverseGuides() {
        return guides.stream();
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
