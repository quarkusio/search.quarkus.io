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
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.search.app.QuarkusVersions;
import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.entity.I18nData;
import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.indexing.FailureCollector;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.GitCloneDirectory;
import io.quarkus.search.app.util.GitInputProvider;
import io.quarkus.search.app.util.UrlInputProvider;

import org.hibernate.search.util.common.impl.Closer;

import org.fedorahosted.tennera.jgettext.Catalog;
import org.fedorahosted.tennera.jgettext.Message;
import org.fedorahosted.tennera.jgettext.PoParser;
import org.yaml.snakeyaml.Yaml;

public class QuarkusIO implements AutoCloseable {

    public static final String QUARKUS_ORIGIN = "quarkus";
    private static final String QUARKIVERSE_ORIGIN = "quarkiverse";
    public static final GitCloneDirectory.Branches MAIN_BRANCHES = new GitCloneDirectory.Branches(
            "develop", "master");
    public static final GitCloneDirectory.Branches LOCALIZED_BRANCHES = new GitCloneDirectory.Branches(
            "main", "docs");

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

    public static String localizedHtmlPath(String path) {
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
    private final CloseableDirectory prefetchedGuides = CloseableDirectory.temp("quarkiverse-guides-");
    private final FailureCollector failureCollector;

    public QuarkusIO(QuarkusIOConfig config, GitCloneDirectory mainRepository,
            Map<Language, GitCloneDirectory> localizedSites, FailureCollector failureCollector) throws IOException {
        this.webUri = config.webUri();
        this.mainRepository = mainRepository;
        this.localizedSites = Collections.unmodifiableMap(localizedSites);
        this.localizedSiteUris = localizedSites.entrySet().stream().collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> config.localized().get(e.getKey().code).webUri()));
        this.failureCollector = failureCollector;
    }

    @Override
    public void close() throws Exception {
        try (var closer = new Closer<Exception>()) {
            closer.push(CloseableDirectory::close, prefetchedGuides);
            closer.push(GitCloneDirectory::close, mainRepository);
            closer.pushAll(GitCloneDirectory::close, localizedSites.values());
        }
    }

    public Stream<Guide> guides() throws IOException {
        return Stream.concat(versionedGuides(), legacyGuides());
    }

    // guides based on the info from the _data/versioned/[version]/index/
    // may contain quarkus.yaml as well as quarkiverse.yml
    private Stream<Guide> versionedGuides() throws IOException {
        return Files.list(mainRepository.resolve("_data").resolve("versioned"))
                .flatMap(p -> {
                    var quarkusVersion = p.getFileName().toString().replace('-', '.');
                    Path quarkiverse = p.resolve("index").resolve("quarkiverse.yaml");
                    Path quarkus = p.resolve("index").resolve("quarkus.yaml");
                    Map<Language, Catalog> translations = translations(
                            (directory, language) -> resolveTranslationPath(p.getFileName().toString(),
                                    quarkus.getFileName().toString(), directory, language));

                    Stream<Guide> quarkusGuides = parseYamlMetadata(webUri, quarkus, quarkusVersion)
                            .flatMap(guide -> translateGuide(guide, translations));
                    if (Files.exists(quarkiverse)) {
                        // the full content won't be translated, but the title/summary may be,
                        // so we want to get that info out if available
                        Map<Language, Catalog> quarkiverseTranslations = translations(
                                (directory, language) -> resolveTranslationPath(p.getFileName().toString(),
                                        quarkiverse.getFileName().toString(), directory, language));
                        return Stream.concat(
                                quarkusGuides,
                                parseYamlQuarkiverseMetadata(quarkiverse, quarkusVersion)
                                        .flatMap(guide -> translateGuide(guide, quarkiverseTranslations)));
                    } else {
                        return quarkusGuides;
                    }
                });
    }

    private static Path resolveTranslationPath(String version, String filename, GitCloneDirectory directory,
            Language language) {
        return directory.resolve(
                Path.of("l10n", "po", language.locale, "_data", "versioned", version, "index", filename + ".po"));
    }

    // older version guides like guides-2-7.yaml or guides-2-13.yaml
    private Stream<Guide> legacyGuides() throws IOException {
        return Files.list(mainRepository.resolve("_data"))
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
        return directory.resolve(
                Path.of("l10n", "po", language.locale, "_data", filename + ".po"));
    }

    @SuppressWarnings("unchecked")
    private Stream<Guide> parseYamlMetadata(URI webUri, Path quarkusYamlPath, String quarkusVersion) {
        return parse(quarkusYamlPath, quarkusYaml -> {
            Set<Guide> parsed = new HashSet<>();
            for (Map<String, Object> parsedGuide : ((Map<String, List<Object>>) quarkusYaml.get("types")).entrySet()
                    .stream()
                    .flatMap(e -> e.getValue().stream())
                    .map(e -> (Map<String, Object>) e).toList()) {

                Guide guide = createGuide(webUri, quarkusVersion, toString(parsedGuide.get("type")), parsedGuide, "summary");
                guide.categories = toSet(parsedGuide.get("categories"));
                guide.keywords.set(Language.ENGLISH, toString(parsedGuide.get("keywords")));
                guide.topics = toSet(parsedGuide.get("topics")).stream()
                        .map(v -> new I18nData<>(Language.ENGLISH, v))
                        .collect(Collectors.toList());
                guide.extensions = toSet(parsedGuide.get("extensions"));

                parsed.add(guide);
            }

            return parsed.stream();
        });
    }

    @SuppressWarnings("unchecked")
    private Stream<Guide> parseYamlQuarkiverseMetadata(Path quarkusYamlPath, String quarkusVersion) {
        return parse(quarkusYamlPath, quarkusYaml -> {
            Set<Guide> parsed = new HashSet<>();
            for (Map.Entry<String, List<Map<String, Object>>> type : ((Map<String, List<Map<String, Object>>>) quarkusYaml
                    .get("types")).entrySet()) {
                for (Map<String, Object> parsedGuide : type.getValue()) {
                    Guide guide = createQuarkiverseGuide(quarkusVersion, type.getKey(), parsedGuide, "summary");
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
                failureCollector.warning(FailureCollector.Stage.TRANSLATION,
                        "Unable to parse a translation file " + path + " : " + e.getMessage(), e);

                messages = new Catalog();
            }
            map.put(language, messages);
        }
        return map;
    }

    private Stream<Guide> translateGuide(Guide guide, Map<Language, Catalog> translations) {
        if (guide.language == null) {
            // Quarkiverse guides have a single URL, because content is not translated,
            // so there is a single Guide instance with translated maps for some of its metadata.
            translateAllForSameGuide(guide.title, translations);
            translateAllForSameGuide(guide.summary, translations);
            translateAllForSameGuide(guide.keywords, translations);
            guide.topics.forEach(topics -> translateAllForSameGuide(topics, translations));
            return Stream.of(guide);
        }
        return Stream.concat(
                Stream.of(guide),
                localizedSites.entrySet().stream().map(entry -> {
                    Language language = entry.getKey();
                    GitCloneDirectory repository = entry.getValue();
                    Catalog messages = translations.get(language);

                    Guide translated = new Guide();
                    translated.url = localizedUrl(language, guide);
                    translated.language = language;
                    translated.type = guide.type;
                    translated.quarkusVersion = guide.quarkusVersion;
                    translated.origin = guide.origin;
                    translated.title = translateOneForNewGuide(guide.title, language, messages);
                    translated.summary = translateOneForNewGuide(guide.summary, language, messages);
                    GitInputProvider gitInputProvider = new GitInputProvider(
                            repository.git(), repository.pagesTree(),
                            localizedHtmlPath(guide.url.getPath()));
                    if (!gitInputProvider.isFileAvailable()) {
                        // if  a file is not present we do not want to add such guide. Since if the html is not there
                        // it means that users won't be able to open it on the site, and returning it in the search results make it pointless.
                        failureCollector.warning(FailureCollector.Stage.TRANSLATION,
                                "Guide " + translated
                                        + " is ignored since we were not able to find an HTML content file for it.");
                        return null;
                    }
                    translated.htmlFullContentProvider.set(language, gitInputProvider);
                    translated.categories = guide.categories;
                    translated.extensions = guide.extensions;
                    translated.keywords = translateOneForNewGuide(guide.keywords, language, messages);
                    translated.topics = guide.topics.stream()
                            .map(v -> translateOneForNewGuide(v, language, messages))
                            .collect(Collectors.toList());

                    return translated;
                }).filter(Objects::nonNull));
    }

    private static void translateAllForSameGuide(I18nData<String> data, Map<Language, Catalog> translations) {
        String key = data.get(Language.ENGLISH);
        if (key == null) {
            // No translation
            return;
        }
        for (Map.Entry<Language, Catalog> entry : translations.entrySet()) {
            data.set(entry.getKey(), translate(entry.getValue(), key));
        }
    }

    private static I18nData<String> translateOneForNewGuide(I18nData<String> oldData,
            Language language, Catalog translations) {
        String key = oldData.get(Language.ENGLISH);
        I18nData<String> translatedData = new I18nData<>();
        if (key == null) {
            // No translation
            return translatedData;
        }
        translatedData.set(language, translate(translations, key));
        return translatedData;
    }

    private static String translate(Catalog messages, String key) {
        if (key == null || key.isBlank()) {
            return key;
        }
        Message message = messages.locateMessage(null, key);
        // > If an entry is marked with "fuzzy", it is not reviewed by human, not published to the localized site,
        // so the original english text should be indexed instead.
        return message == null || message.isFuzzy()
                ? key
                // sometimes message might be an empty string. In that case we will return the original text
                : message.getMsgstr() == null || message.getMsgstr().isBlank() ? key : message.getMsgstr();
    }

    private URI localizedUrl(Language language, Guide guide) {
        URI url = guide.url;
        try {
            URI localized = localizedSiteUris.get(language);
            return new URI(
                    localized.getScheme(), localized.getAuthority(), url.getPath(),
                    url.getQuery(), url.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "Cannot create a localized version of the URL (%s). It is expected to have a correctly formatted URL at this point to a Quarkiverse guide (i.e. http://smth.smth/smth) : %s"
                            .formatted(url, e.getMessage()),
                    e);
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
            throw new IllegalStateException("Unable to load %s: %s".formatted(quarkusYamlPath, e.getMessage()), e);
        }

        return parser.apply(quarkusYaml);
    }

    private Guide createGuide(URI urlBase, String quarkusVersion, String type, Map<String, Object> parsedGuide,
            String summaryKey) {
        String parsedUrl = toString(parsedGuide.get("url"));
        if (parsedUrl.startsWith("http")) {
            // we are looking at a quarkiverse guide:
            return createQuarkiverseGuide(quarkusVersion, type, parsedGuide, summaryKey);
        } else {
            return createCoreGuide(urlBase, quarkusVersion, type, parsedGuide, summaryKey);
        }
    }

    private Guide createCoreGuide(URI urlBase, String quarkusVersion, String type, Map<String, Object> parsedGuide,
            String summaryKey) {
        Guide guide = new Guide();
        guide.quarkusVersion = quarkusVersion;
        guide.language = Language.ENGLISH;
        guide.origin = toString(parsedGuide.get("origin"));
        if (guide.origin == null) {
            guide.origin = QUARKUS_ORIGIN;
        }
        guide.type = type;
        guide.title.set(Language.ENGLISH, renderMarkdown(toString(parsedGuide.get("title"))));
        guide.summary.set(Language.ENGLISH, renderMarkdown(toString(parsedGuide.get(summaryKey))));
        String parsedUrl = toString(parsedGuide.get("url"));
        guide.url = httpUrl(urlBase, quarkusVersion, parsedUrl);
        guide.htmlFullContentProvider.set(Language.ENGLISH,
                new GitInputProvider(mainRepository.git(), mainRepository.pagesTree(),
                        htmlPath(quarkusVersion, parsedUrl)));
        return guide;
    }

    private Guide createQuarkiverseGuide(String quarkusVersion, String type, Map<String, Object> parsedGuide,
            String summaryKey) {
        Guide guide = new Guide();
        guide.quarkusVersion = quarkusVersion;
        // This is on purpose and will lead to the same guide instance being used for all languages
        guide.language = null;
        guide.origin = toString(parsedGuide.get("origin"));
        if (guide.origin == null) {
            guide.origin = QUARKIVERSE_ORIGIN;
        }
        guide.type = type;
        guide.title.set(Language.ENGLISH, renderMarkdown(toString(parsedGuide.get("title"))));
        guide.summary.set(Language.ENGLISH, renderMarkdown(toString(parsedGuide.get(summaryKey))));
        String parsedUrl = toString(parsedGuide.get("url"));
        guide.url = httpUrl(quarkusVersion, parsedUrl);
        guide.htmlFullContentProvider.set(Language.ENGLISH,
                new UrlInputProvider(prefetchedGuides, guide.url, failureCollector));
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
        if (value instanceof String string) {
            return toSet(string);
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
