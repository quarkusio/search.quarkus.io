package io.quarkus.search.app.quarkusio;

import static io.quarkus.search.app.util.MarkdownRenderer.renderMarkdown;

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

import io.quarkus.search.app.QuarkusVersions;
import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.GitInputProvider;
import io.quarkus.search.app.util.GitUtils;
import io.quarkus.search.app.util.UrlInputProvider;

import org.hibernate.search.util.common.impl.Closer;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevTree;
import org.yaml.snakeyaml.Yaml;

public class QuarkusIO implements AutoCloseable {

    private static final String QUARKUS_ORIGIN = "quarkus";
    private static final String QUARKIVERSE_ORIGIN = "quarkiverse";
    public static final String SOURCE_BRANCH = "develop";
    public static final String PAGES_BRANCH = "master";

    public static URI httpUrl(URI urlBase, String version, String name) {
        return urlBase.resolve(httpPath(version, name));
    }

    private static URI httpUrl(String version, String uri) {
        try {
            return new URI(uri + "?quarkusDocVersion=" + version);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Unable to parse an URL: " + uri, e);
        }
    }

    public static String htmlPath(String version, String name) {
        return httpPath(version, name) + ".html";
    }

    private static String httpPath(String version, String name) {
        return QuarkusVersions.LATEST.equals(version) ? name
                : "version/" + version + name;
    }

    public static Path yamlMetadataPath(String version) {
        return Path.of("_data", "versioned", version.replace('.', '-'), "index", "quarkus.yaml");
    }

    public static Path yamlQuarkiverseMetadataPath(String version) {
        return Path.of("_data", "versioned", version.replace('.', '-'), "index", "quarkiverse.yaml");
    }

    private final URI webUri;
    private final CloseableDirectory directory;
    private final CloseableDirectory prefetchedQuarkiverseGuides = CloseableDirectory.temp("quarkiverse-guides-");
    private final Git git;
    private final RevTree pagesTree;

    public QuarkusIO(QuarkusIOConfig config, CloseableDirectory directory, Git git) throws IOException {
        this.webUri = config.webUri();
        this.directory = directory;
        this.git = git;
        this.pagesTree = GitUtils.firstExistingRevTree(git.getRepository(), "origin/" + PAGES_BRANCH);
    }

    @Override
    public void close() throws Exception {
        try (var closer = new Closer<Exception>()) {
            closer.push(Git::close, git);
            closer.push(CloseableDirectory::close, directory);
            closer.push(CloseableDirectory::close, prefetchedQuarkiverseGuides);
        }
    }

    @SuppressWarnings("resource")
    public Stream<Guide> guides() throws IOException {
        return Stream.concat(versionedGuides(), legacyGuides());
    }

    // guides based on the info from the _data/versioned/[version]/index/
    // may contain quarkus.yaml as well as quarkiverse.yml
    private Stream<Guide> versionedGuides() throws IOException {
        return Files.list(directory.path().resolve("_data").resolve("versioned"))
                .flatMap(p -> {
                    var version = p.getFileName().toString().replace('-', '.');
                    Path quarkiverse = p.resolve("index").resolve("quarkiverse.yaml");
                    Path quarkus = p.resolve("index").resolve("quarkus.yaml");

                    Stream<Guide> quarkusGuides = parseYamlMetadata(webUri, quarkus, version);
                    if (Files.exists(quarkiverse)) {
                        return Stream.concat(
                                quarkusGuides,
                                parseYamlQuarkiverseMetadata(webUri, quarkiverse, version));
                    } else {
                        return quarkusGuides;
                    }
                });
    }

    // older version guides like guides-2-7.yaml or guides-2-13.yaml
    private Stream<Guide> legacyGuides() throws IOException {
        return Files.list(directory.path().resolve("_data"))
                .filter(p -> !Files.isDirectory(p) && p.getFileName().toString().startsWith("guides-"))
                .flatMap(p -> {
                    var version = p.getFileName().toString().replaceAll("guides-|\\.yaml", "").replace('-', '.');
                    return parseYamlLegacyMetadata(webUri, p, version);
                });
    }

    @SuppressWarnings("unchecked")
    private Stream<Guide> parseYamlMetadata(URI webUri, Path quarkusYamlPath, String version) {
        return parse(quarkusYamlPath, quarkusYaml -> {
            Set<Guide> parsed = new HashSet<>();
            for (Map<String, Object> parsedGuide : ((Map<String, List<Object>>) quarkusYaml.get("types")).entrySet()
                    .stream()
                    .flatMap(e -> e.getValue().stream())
                    .map(e -> (Map<String, Object>) e).toList()) {

                Guide guide = createGuide(webUri, version, toString(parsedGuide.get("type")), parsedGuide, "summary");
                guide.categories = toSet(parsedGuide.get("categories"));
                guide.keywords = toString(parsedGuide.get("keywords"));
                guide.topics = toSet(parsedGuide.get("topics"));
                guide.extensions = toSet(parsedGuide.get("extensions"));

                parsed.add(guide);
            }

            return parsed.stream();
        });
    }

    @SuppressWarnings("unchecked")
    private Stream<Guide> parseYamlQuarkiverseMetadata(URI webUri, Path quarkusYamlPath, String version) {
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

            return parsed.stream();
        });
    }

    @SuppressWarnings("unchecked")
    private Stream<Guide> parseYamlLegacyMetadata(URI webUri, Path quarkusYamlPath, String version) {
        return parse(quarkusYamlPath, quarkusYaml -> {
            Map<URI, Guide> parsed = new HashMap<>();
            for (Map<String, Object> categoryObj : ((List<Map<String, Object>>) quarkusYaml.get("categories"))) {
                String category = toString(categoryObj.get("cat-id"));
                for (Map<String, Object> parsedGuide : ((List<Map<String, Object>>) categoryObj.get("guides"))) {
                    Guide guide = createGuide(webUri, version, "guide", parsedGuide, "description");
                    // since we can have the same link to a quarkiverse guide in multiple versions of quarkus,
                    // we want to somehow make them different in their ID:
                    guide.categories = Set.of(category);
                    Guide old = parsed.put(guide.url, guide);
                    if (old != null) {
                        guide.categories = combine(guide.categories, old.categories);
                    }
                }
            }

            return parsed.values().stream();
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

    private static Stream<Guide> parse(Path quarkusYamlPath,
            Function<Map<String, Object>, Stream<Guide>> parser) {
        Map<String, Object> quarkusYaml;
        try (InputStream inputStream = Files.newInputStream(quarkusYamlPath)) {
            Yaml yaml = new Yaml();
            quarkusYaml = yaml.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + quarkusYamlPath, e);
        }

        return parser.apply(quarkusYaml);
    }

    private Guide createGuide(URI webUri, String version, String type, Map<String, Object> parsedGuide,
            String summaryKey) {
        Guide guide = new Guide();
        guide.type = type;
        guide.title = renderMarkdown(toString(parsedGuide.get("title")));
        guide.origin = toString(parsedGuide.get("origin"));
        guide.version = version;
        guide.summary = renderMarkdown(toString(parsedGuide.get(summaryKey)));
        String parsedUrl = toString(parsedGuide.get("url"));
        URI uri;
        if (parsedUrl.startsWith("http")) {
            // we are looking at a quarkiverse guide:
            uri = httpUrl(version, parsedUrl);
            guide.htmlFullContentProvider = new UrlInputProvider(prefetchedQuarkiverseGuides, uri);

            if (guide.origin == null) {
                guide.origin = QUARKIVERSE_ORIGIN;
            }
        } else {
            uri = httpUrl(webUri, version, parsedUrl);
            guide.htmlFullContentProvider = new GitInputProvider(git, pagesTree, htmlPath(version, parsedUrl));

            if (guide.origin == null) {
                guide.origin = QUARKUS_ORIGIN;
            }
        }
        guide.url = uri;
        return guide;
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
