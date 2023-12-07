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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.search.app.QuarkusVersions;
import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.GitCloneDirectory;
import io.quarkus.search.app.util.GitInputProvider;
import io.quarkus.search.app.util.UrlInputProvider;

import io.quarkus.logging.Log;

import org.hibernate.search.util.common.impl.Closer;

import org.fedorahosted.tennera.jgettext.Catalog;
import org.fedorahosted.tennera.jgettext.Message;
import org.fedorahosted.tennera.jgettext.PoParser;
import org.yaml.snakeyaml.Yaml;

public class QuarkusIO implements AutoCloseable {

    public static final String QUARKUS_ORIGIN = "quarkus";
    private static final String QUARKIVERSE_ORIGIN = "quarkiverse";
    public static final String SOURCE_BRANCH = "develop";
    public static final String PAGES_BRANCH = "master";
    public static final String LOCALIZED_SOURCE_BRANCH = "main";
    public static final String LOCALIZED_PAGES_BRANCH = "docs";

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

    public static String localizedHtmlPath(String version, String path) {
        return "docs" + path + ".html";
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
    private final GitCloneDirectory mainRepository;
    private final Map<Language, GitCloneDirectory> localizedSites;
    private final Map<Language, URI> localizedSiteUris;
    private final CloseableDirectory prefetchedQuarkiverseGuides = CloseableDirectory.temp("quarkiverse-guides-");

    public QuarkusIO(QuarkusIOConfig config, GitCloneDirectory mainRepository,
            Map<Language, GitCloneDirectory> localizedSites) throws IOException {
        this.webUri = config.webUri();
        this.mainRepository = mainRepository;
        this.localizedSites = Collections.unmodifiableMap(localizedSites);
        this.localizedSiteUris = localizedSites.entrySet().stream().collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> config.localized().get(e.getKey().code).webUri()));
    }

    @Override
    public void close() throws Exception {
        try (var closer = new Closer<Exception>()) {
            closer.push(GitCloneDirectory::close, mainRepository);
            closer.push(CloseableDirectory::close, prefetchedQuarkiverseGuides);
            closer.pushAll(GitCloneDirectory::close, localizedSites.values());
        }
    }

    @SuppressWarnings("resource")
    public Stream<Guide> guides() throws IOException {
        return Stream.concat(versionedGuides(), legacyGuides());
    }

    // guides based on the info from the _data/versioned/[version]/index/
    // may contain quarkus.yaml as well as quarkiverse.yml
    private Stream<Guide> versionedGuides() throws IOException {
        return Files.list(mainRepository.directory().path().resolve("_data").resolve("versioned"))
                .flatMap(p -> {
                    var version = p.getFileName().toString().replace('-', '.');
                    Path quarkiverse = p.resolve("index").resolve("quarkiverse.yaml");
                    Path quarkus = p.resolve("index").resolve("quarkus.yaml");
                    Map<Language, Catalog> translations = translations(
                            (directory, language) -> resolveTranslationPath(p.getFileName().toString(),
                                    quarkus.getFileName().toString(), directory, language));

                    Stream<Guide> quarkusGuides = parseYamlMetadata(webUri, quarkus, version)
                            .flatMap(guide -> translateGuide(guide, translations));
                    if (Files.exists(quarkiverse)) {
                        // the full content won't be translated, but the title/summary may be, so we want to get that info out if available
                        // we also have to create "copies" of quarkiverse guides for other languages, otherwise we won't find them in search results
                        // as we are using a `must` filter on language:
                        Map<Language, Catalog> quarkiverseTranslations = translations(
                                (directory, language) -> resolveTranslationPath(p.getFileName().toString(),
                                        quarkiverse.getFileName().toString(), directory, language));
                        return Stream.concat(
                                quarkusGuides,
                                parseYamlQuarkiverseMetadata(webUri, quarkiverse, version)
                                        .flatMap(guide -> translateGuide(guide, quarkiverseTranslations)));
                    } else {
                        return quarkusGuides;
                    }
                });
    }

    private static Path resolveTranslationPath(String version, String filename, GitCloneDirectory directory,
            Language language) {
        return directory.directory().path().resolve(
                Path.of("l10n", "po", language.locale, "_data", "versioned", version, "index", filename + ".po"));
    }

    // older version guides like guides-2-7.yaml or guides-2-13.yaml
    private Stream<Guide> legacyGuides() throws IOException {
        return Files.list(mainRepository.directory().path().resolve("_data"))
                .filter(p -> !Files.isDirectory(p) && p.getFileName().toString().startsWith("guides-"))
                .flatMap(p -> {
                    var version = p.getFileName().toString().replaceAll("guides-|\\.yaml", "").replace('-', '.');

                    Map<Language, Catalog> translations = translations(
                            (directory, language) -> resolveLegacyTranslationPath(p.getFileName().toString(), directory,
                                    language));

                    return parseYamlLegacyMetadata(webUri, p, version)
                            .flatMap(guide -> translateGuide(guide, translations));
                });
    }

    private static Path resolveLegacyTranslationPath(String filename, GitCloneDirectory directory, Language language) {
        return directory.directory().path().resolve(
                Path.of("l10n", "po", language.locale, "_data", filename + ".po"));
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

    private Map<Language, Catalog> translations(BiFunction<GitCloneDirectory, Language, Path> translationPathResolver) {
        Map<Language, Catalog> map = new HashMap<>();
        for (Map.Entry<Language, GitCloneDirectory> entry : localizedSites.entrySet()) {
            Language language = entry.getKey();
            GitCloneDirectory directory = entry.getValue();
            Path path = translationPathResolver.apply(directory, language);
            Catalog messages;
            try {
                messages = new PoParser().parseCatalog(path.toFile());
            } catch (IOException e) {
                // it may be that not all localized sites are up-to-date, in that case we just assume that the translation is not there
                // and the non-translated english text will be used.
                Log.error("Unable to parse a translation file " + path + " : " + e.getMessage(), e);
                messages = new Catalog();
            }
            map.put(language, messages);
        }
        return map;
    }

    private Stream<? extends Guide> translateGuide(Guide guide, Map<Language, Catalog> transaltions) {
        return Stream.concat(
                Stream.of(guide),
                localizedSites.entrySet().stream().map(entry -> {
                    Language language = entry.getKey();
                    GitCloneDirectory repository = entry.getValue();
                    Catalog messages = transaltions.get(language);

                    Guide translated = new Guide();
                    translated.url = localizedUrl(language, guide);
                    translated.language = language;
                    translated.type = guide.type;
                    translated.version = guide.version;
                    translated.origin = guide.origin;
                    translated.title = translate(messages, guide.title);
                    translated.summary = translate(messages, guide.summary);
                    // If it is a quarkiverse guide, it means that it is an external url, we can't do much about it
                    // and we just use the same provider/file that we've already used for the original guide in English;
                    // otherwise we try to find a corresponding translated HTML:
                    translated.htmlFullContentProvider = guide.quarkusGuide()
                            ? new GitInputProvider(
                                    repository.git(), repository.pagesTree(),
                                    localizedHtmlPath(guide.version, guide.url.getPath()))
                            : guide.htmlFullContentProvider;
                    translated.categories = guide.categories;
                    translated.extensions = guide.extensions;
                    translated.keywords = translate(messages, guide.keywords);
                    translated.topics = guide.topics;

                    return translated;
                }));
    }

    private static String translate(Catalog messages, String key) {
        if (key == null || key.isBlank()) {
            return key;
        }
        Message message = messages.locateMessage(null, key);
        // > If an entry is marked with "fuzzy", it is not reviewed by human, not published to the localized site,
        // so the original english text should be indexed instead.
        return message == null || message.isFuzzy() ? key : message.getMsgstr();
    }

    private URI localizedUrl(Language language, Guide guide) {
        URI url = guide.url;
        try {
            // if we have a Quarkus "local" guide then we have to replace the "host" part to use the localized one
            // that we store in the web uris:
            if (guide.quarkusGuide()) {
                URI localized = localizedSiteUris.get(language);
                return new URI(
                        localized.getScheme(), localized.getAuthority(), url.getPath(),
                        url.getQuery(), url.getFragment());
            } else {
                // otherwise since the link for Quarkiverse (external) guides is exactly the same for all the languages/versions
                // and we've already added a version parameter to the query part of the url, we just append the language to it
                // to make it unique:
                return new URI(
                        url.getScheme(), url.getAuthority(), url.getPath(),
                        url.getQuery() + "&language=" + language.code, url.getFragment());
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot create a localized version of the URL: " + url, e);
        }
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
        guide.language = Language.ENGLISH;
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
            guide.htmlFullContentProvider = new GitInputProvider(mainRepository.git(), mainRepository.pagesTree(),
                    htmlPath(version, parsedUrl));

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
