package io.quarkus.search.app.quarkusio;

import static io.quarkus.search.app.util.MarkdownRenderer.renderMarkdown;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.search.app.QuarkusVersions;
import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.entity.I18nData;
import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.indexing.reporting.FailureCollector;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.GitCloneDirectory;
import io.quarkus.search.app.util.GitInputProvider;
import io.quarkus.search.app.util.GitUtils;
import io.quarkus.search.app.util.UrlInputProvider;

import org.hibernate.search.util.common.impl.Closer;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.fedorahosted.tennera.jgettext.Catalog;
import org.fedorahosted.tennera.jgettext.Message;
import org.fedorahosted.tennera.jgettext.PoParser;
import org.yaml.snakeyaml.Yaml;

public class QuarkusIO implements Closeable {

    public static final String QUARKUS_ORIGIN = "quarkus";
    private static final String QUARKIVERSE_ORIGIN = "quarkiverse";
    public static final GitCloneDirectory.Branches MAIN_BRANCHES = new GitCloneDirectory.Branches(
            "main", "gh-pages");
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

    public static String htmlPath(Language language, String version, String name) {
        String htmlPath = httpPath(version, name) + ".html";
        return (Language.ENGLISH.equals(language) ? "" : "docs" + (htmlPath.startsWith("/") ? "" : "/")) + htmlPath;
    }

    private static String httpPath(String version, String name) {
        return QuarkusVersions.LATEST.equals(version) ? name
                : "version/" + version + name;
    }

    public static Path yamlMetadataPath(String version) {
        return Path.of("_data", "versioned", version.replace('.', '-'), "index", "quarkus.yaml");
    }

    public static Path yamlVersionMetadataPath() {
        return Path.of("_data", "versions.yaml");
    }

    public static Path yamlQuarkiverseMetadataPath(String version) {
        return Path.of("_data", "versioned", version.replace('.', '-'), "index", "quarkiverse.yaml");
    }

    private final Map<Language, QuarkusIOCloneDirectory> allSites;
    private final Map<Language, URI> siteUris;
    private final CloseableDirectory prefetchedGuides = CloseableDirectory.temp("quarkiverse-guides-");
    private final FailureCollector failureCollector;

    public QuarkusIO(QuarkusIOConfig config, GitCloneDirectory mainRepository,
            Map<Language, GitCloneDirectory> localizedSites, FailureCollector failureCollector) throws IOException {
        HashMap<Language, URI> languageUriMap = new HashMap<>(localizedSites.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> config.localized().get(e.getKey().code).webUri())));
        languageUriMap.put(Language.ENGLISH, config.webUri());
        this.siteUris = Collections.unmodifiableMap(languageUriMap);
        this.failureCollector = failureCollector;

        Map<Language, QuarkusIOCloneDirectory> all = new HashMap<>();
        all.put(Language.ENGLISH, new QuarkusIOCloneDirectory(failureCollector, mainRepository));
        for (var entry : localizedSites.entrySet()) {
            all.put(entry.getKey(), new QuarkusIOCloneDirectory(failureCollector, entry.getValue()));
        }
        this.allSites = Collections.unmodifiableMap(all);

        validateRepositories(mainRepository, localizedSites, failureCollector);
    }

    private static void validateRepositories(
            GitCloneDirectory mainRepository,
            Map<Language, GitCloneDirectory> localizedSites,
            FailureCollector failureCollector) {
        ObjectId latestMainRev = mainRepository.currentSourcesLatestHash();
        for (Map.Entry<Language, GitCloneDirectory> localized : localizedSites.entrySet()) {
            localized.getValue().currentUpstreamSubmoduleSourcesHash().ifPresentOrElse(commitHash -> {
                if (latestMainRev.equals(commitHash)) {
                    // means that the localized repo points to the latest main one and we can ignore further checks
                    return;
                }

                // Otherwise let's see when the localized site was last synced,
                // by looking at when that latest commit was committed:
                Instant lastSync = GitUtils.committedInstant(mainRepository.git(), commitHash);
                // Sync happens each week, so we can have
                //  1. a commit to main repo on Monday
                //  2. no further commits during the week
                //  3. sync on the weekend (Sunday?)
                //  4. commits to main repo
                //  at this point the difference between latest commit in localized site and current date can be
                //  more than 7 days; so we give it 2 weeks period before we start considering that there's a sync problem:
                Duration duration = Duration.between(lastSync, Instant.now());
                if (duration.compareTo(Duration.ofDays(14)) > 0) {
                    failureCollector.warning(
                            FailureCollector.Stage.TRANSLATION,
                            "Localized repository '" + localized.getKey() + "' is out of sync for " + duration);
                }
            }, () -> {
                failureCollector.warning(
                        FailureCollector.Stage.PARSING,
                        "Repository for a localized site '" + localized.getKey()
                                + "' does not have an \"upstream\" directory. Site will be considered up-to-date. ");
            });
        }
    }

    @Override
    public void close() throws IOException {
        for (QuarkusIOCloneDirectory directory : allSites.values()) {
            directory.unprocessed().ifPresent(m -> failureCollector.warning(FailureCollector.Stage.PARSING, m));
        }
        try (var closer = new Closer<IOException>()) {
            closer.push(CloseableDirectory::close, prefetchedGuides);
            closer.pushAll(QuarkusIOCloneDirectory::close, allSites.values());
        }
    }

    public Stream<Guide> guides() throws IOException {
        return Stream.concat(
                Stream.concat(versionedGuides(), legacyGuides()),
                Stream.concat(quarkiverseGuides(), legacyQuarkiverseGuides()));
    }

    // guides based on the info from the _data/versioned/[version]/index/
    private Stream<Guide> versionedGuides() {
        return allSites.entrySet().stream()
                .flatMap(entry -> {
                    Language language = entry.getKey();
                    GitCloneDirectory cloneDirectory = entry.getValue().cloneDirectory();
                    VersionFilter versionFilter = entry.getValue().versionFilter();
                    RevTree translationSourcesTree = cloneDirectory.sourcesTranslationTree();
                    Repository repository = cloneDirectory.git().getRepository();

                    return cloneDirectory.sourcesFileStream("_data/versioned", path -> path.endsWith("quarkus.yaml"))
                            .map(QuarkusIO::extractVersion)
                            .filter(versionFilter)
                            .flatMap(quarkusVersion -> {
                                String quarkus = quarkusVersion.path();

                                Catalog translations = translations(
                                        repository, translationSourcesTree,
                                        resolveTranslationPath(
                                                quarkusVersion.versionDirectory(), "quarkus.yaml", language));

                                try (InputStream file = cloneDirectory.sourcesFile(quarkus)) {
                                    return parseYamlMetadata(cloneDirectory, file, quarkusVersion.version(), language,
                                            translations);
                                } catch (IOException e) {
                                    throw new IllegalStateException(
                                            "Unable to load %s: %s".formatted(quarkusVersion.path(), e.getMessage()),
                                            e);
                                }
                            });
                });
    }

    // older version guides like guides-2-7.yaml or guides-2-13.yaml
    private Stream<Guide> legacyGuides() {
        return allSites.entrySet().stream()
                .flatMap(entry -> {
                    Language language = entry.getKey();
                    GitCloneDirectory cloneDirectory = entry.getValue().cloneDirectory();
                    VersionFilter versionFilter = entry.getValue().versionFilter();
                    RevTree translationSourcesTree = cloneDirectory.sourcesTranslationTree();
                    Repository repository = cloneDirectory.git().getRepository();

                    return cloneDirectory.sourcesFileStream("_data", path -> path.matches("_data/guides-\\d+-\\d+\\.yaml"))
                            .map(QuarkusIO::extractLegacyVersion)
                            .filter(versionFilter)
                            .flatMap(quarkusVersion -> {
                                String quarkus = quarkusVersion.path();

                                Catalog translations = translations(
                                        repository, translationSourcesTree,
                                        resolveLegacyTranslationPath(quarkusVersion.versionDirectory(), language));

                                try (InputStream file = cloneDirectory.sourcesFile(quarkus)) {
                                    return parseYamlLegacyMetadata(cloneDirectory, file, quarkusVersion.version(), language,
                                            translations);
                                } catch (IOException e) {
                                    throw new IllegalStateException(
                                            "Unable to load %s: %s".formatted(quarkusVersion.path(), e.getMessage()),
                                            e);
                                }
                            });
                });
    }

    private Stream<Guide> quarkiverseGuides() {
        Language language = Language.ENGLISH;
        QuarkusIOCloneDirectory quarkusIOCloneDirectory = allSites.get(language);
        GitCloneDirectory cloneDirectory = quarkusIOCloneDirectory.cloneDirectory();
        VersionFilter versionFilter = quarkusIOCloneDirectory.versionFilter();

        return cloneDirectory.sourcesFileStream("_data/versioned", path -> path.endsWith("quarkiverse.yaml"))
                .map(QuarkusIO::extractQuarkiverseVersion)
                .filter(versionFilter)
                .flatMap(quarkusVersion -> {
                    String quarkus = quarkusVersion.path();

                    Map<Language, Catalog> translations = createTranslations(
                            lang -> resolveTranslationPath(quarkusVersion.versionDirectory(), "quarkiverse.yaml", lang));

                    try (InputStream file = cloneDirectory.sourcesFile(quarkus)) {
                        return parseYamlQuarkiverseMetadata(file, quarkusVersion.version(), translations);
                    } catch (IOException e) {
                        throw new IllegalStateException(
                                "Unable to load %s: %s".formatted(quarkusVersion.path(), e.getMessage()),
                                e);
                    }
                });
    }

    private Stream<Guide> legacyQuarkiverseGuides() {
        Language language = Language.ENGLISH;
        QuarkusIOCloneDirectory quarkusIOCloneDirectory = allSites.get(language);
        GitCloneDirectory cloneDirectory = quarkusIOCloneDirectory.cloneDirectory();
        VersionFilter versionFilter = quarkusIOCloneDirectory.versionFilter();

        return cloneDirectory.sourcesFileStream("_data", path -> path.matches("_data/guides-\\d+-\\d+\\.yaml"))
                .map(QuarkusIO::extractLegacyVersion)
                .filter(versionFilter)
                .flatMap(quarkusVersion -> {
                    String quarkus = quarkusVersion.path();

                    Map<Language, Catalog> translations = createTranslations(
                            lang -> resolveLegacyTranslationPath(quarkusVersion.versionDirectory(), lang));

                    try (InputStream file = cloneDirectory.sourcesFile(quarkus)) {
                        return parseQuarkiverseYamlLegacyMetadata(cloneDirectory, file, quarkusVersion.version(), translations);
                    } catch (IOException e) {
                        throw new IllegalStateException(
                                "Unable to load %s: %s".formatted(quarkusVersion.path(), e.getMessage()),
                                e);
                    }
                });
    }

    private Map<Language, Catalog> createTranslations(Function<Language, String> pathCreator) {
        Map<Language, Catalog> translations = new HashMap<>();
        for (Map.Entry<Language, QuarkusIOCloneDirectory> entry : allSites.entrySet()) {
            Language lang = entry.getKey();
            GitCloneDirectory cloneDirectory = entry.getValue().cloneDirectory();
            translations.put(lang, translations(
                    cloneDirectory.git().getRepository(), cloneDirectory.sourcesTranslationTree(),
                    pathCreator.apply(lang)));
        }
        return translations;
    }

    private static VersionAndPaths extractVersion(String relativePath) {
        return extractVersion(relativePath, "quarkus.yaml");
    }

    private static VersionAndPaths extractQuarkiverseVersion(String relativePath) {
        return extractVersion(relativePath, "quarkiverse.yaml");
    }

    private static VersionAndPaths extractVersion(String relativePath, String filename) {
        String versionDirectory = relativePath.replace("_data/versioned/", "")
                .replace("/index/", "").replace(filename, "");
        return new VersionAndPaths(versionDirectory.replace('-', '.'), versionDirectory, relativePath);
    }

    private static VersionAndPaths extractLegacyVersion(String relativePath) {
        return new VersionAndPaths(
                relativePath.replace("_data/guides-", "")
                        .replace(".yaml", "")
                        .replace('-', '.'),
                relativePath.replace("_data/", ""),
                relativePath);
    }

    private static String resolveTranslationPath(String version, String filename, Language language) {
        return Path.of("l10n", "po", language.locale, "_data", "versioned", version, "index", filename + ".po").toString();
    }

    private static String resolveLegacyTranslationPath(String filename, Language language) {
        return Path.of("l10n", "po", language.locale, "_data", filename + ".po").toString();
    }

    @SuppressWarnings("unchecked")
    private Stream<Guide> parseYamlMetadata(GitCloneDirectory cloneDirectory, InputStream quarkusYamlPath,
            String quarkusVersion,
            Language language, Catalog messages) {
        return parse(quarkusYamlPath, quarkusYaml -> {
            Set<Guide> parsed = new HashSet<>();
            for (Map<String, Object> parsedGuide : ((Map<String, List<Object>>) quarkusYaml.get("types")).entrySet()
                    .stream()
                    .flatMap(e -> e.getValue().stream())
                    .map(e -> (Map<String, Object>) e).toList()) {

                Guide guide = createGuide(cloneDirectory, quarkusVersion, toString(parsedGuide.get("type")), parsedGuide,
                        "summary", language, messages);
                if (guide == null) {
                    continue;
                }
                guide.categories = toSet(parsedGuide.get("categories"));
                guide.keywords.set(language, translate(messages, toString(parsedGuide.get("keywords"))));
                guide.topics = toSet(parsedGuide.get("topics")).stream()
                        .map(v -> new I18nData<>(language, v))
                        .collect(Collectors.toList());
                guide.extensions = toSet(parsedGuide.get("extensions"));

                parsed.add(guide);
            }

            return parsed.stream();
        });
    }

    @SuppressWarnings("unchecked")
    private Stream<Guide> parseYamlQuarkiverseMetadata(InputStream quarkusYamlPath, String quarkusVersion,
            Map<Language, Catalog> translations) {
        return parse(quarkusYamlPath, quarkusYaml -> {
            Set<Guide> parsed = new HashSet<>();
            for (Map.Entry<String, List<Map<String, Object>>> type : ((Map<String, List<Map<String, Object>>>) quarkusYaml
                    .get("types")).entrySet()) {
                for (Map<String, Object> parsedGuide : type.getValue()) {
                    Guide guide = createQuarkiverseGuide(quarkusVersion, type.getKey(), parsedGuide, "summary");
                    guide.categories = toSet(parsedGuide.get("categories"));

                    // Quarkiverse guides have a single URL, because content is not translated,
                    // so there is a single Guide instance with translated maps for some of its metadata.
                    translateAllForSameGuide(guide.title, translations);
                    translateAllForSameGuide(guide.summary, translations);
                    translateAllForSameGuide(guide.keywords, translations);

                    parsed.add(guide);
                }
            }

            return parsed.stream();
        });
    }

    private Stream<Guide> parseQuarkiverseYamlLegacyMetadata(GitCloneDirectory cloneDirectory, InputStream quarkusYamlPath,
            String version,
            Map<Language, Catalog> translations) {
        return parseYamlLegacyMetadata(
                cloneDirectory, quarkusYamlPath, version, Language.ENGLISH, translations.get(Language.ENGLISH),
                (guide, guides) -> {
                    if (guide.language == null) {
                        translateAllForSameGuide(guide.title, translations);
                        translateAllForSameGuide(guide.summary, translations);
                        translateAllForSameGuide(guide.keywords, translations);
                        guide.topics.forEach(topics -> translateAllForSameGuide(topics, translations));
                        return guides.put(guide.url, guide);
                    }
                    return guide;
                });
    }

    private Stream<Guide> parseYamlLegacyMetadata(GitCloneDirectory cloneDirectory, InputStream quarkusYamlPath, String version,
            Language language, Catalog translations) {
        return parseYamlLegacyMetadata(
                cloneDirectory, quarkusYamlPath, version, language, translations, (guide, guides) -> {
                    if (guide.language == null) {
                        // this would mean that we want to create a quarkiverse guide, that should have all the languages,
                        // we'll ignore it here, and address collecting quarkiverse guides from legacy metadata files in other place.
                        return guide;
                    }
                    return guides.put(guide.url, guide);
                });
    }

    @SuppressWarnings("unchecked")
    private Stream<Guide> parseYamlLegacyMetadata(GitCloneDirectory cloneDirectory, InputStream quarkusYamlPath,
            String version, Language language, Catalog translations, BiFunction<Guide, Map<URI, Guide>, Guide> action) {
        return parse(quarkusYamlPath, quarkusYaml -> {
            Map<URI, Guide> parsed = new HashMap<>();
            for (Map<String, Object> categoryObj : ((List<Map<String, Object>>) quarkusYaml.get("categories"))) {
                String category = toString(categoryObj.get("cat-id"));
                for (Map<String, Object> parsedGuide : ((List<Map<String, Object>>) categoryObj.get("guides"))) {
                    Guide guide = createGuide(cloneDirectory, version, "guide", parsedGuide, "description", language,
                            translations);
                    if (guide == null) {
                        continue;
                    }
                    // since we can have the same link to a quarkiverse guide in multiple versions of quarkus,
                    // we want to somehow make them different in their ID:
                    guide.categories = Set.of(category);

                    Guide old = action.apply(guide, parsed);

                    if (old != null) {
                        guide.categories = combine(guide.categories, old.categories);
                    }
                }
            }

            return parsed.values().stream();
        });
    }

    private Catalog translations(Repository repository, RevTree sources, String path) {
        if (sources == null) {
            // for eng site:
            return new Catalog();
        }

        try (InputStream file = GitUtils.file(repository, sources, path)) {
            return new PoParser().parseCatalog(file, false);
        } catch (IOException | IllegalStateException e) {
            // it may be that not all localized sites are up-to-date, in that case we just assume that the translation is not there
            // and the non-translated english text will be used.
            failureCollector.warning(FailureCollector.Stage.TRANSLATION,
                    "Unable to parse a translation file " + path + " : " + e.getMessage(), e);

            return new Catalog();
        }
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

    private static void putIfNotNull(Map<Language, String> map, Language language, String value) {
        if (value == null) {
            return;
        }
        map.put(language, value);
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

    private static Stream<Guide> parse(InputStream inputStream,
            Function<Map<String, Object>, Stream<Guide>> parser) {
        Map<String, Object> quarkusYaml;
        Yaml yaml = new Yaml();
        quarkusYaml = yaml.load(inputStream);

        return parser.apply(quarkusYaml);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> parseVersions(InputStream inputStream) {
        Map<String, Object> versionsYaml;
        Yaml yaml = new Yaml();
        versionsYaml = yaml.load(inputStream);

        Collection<String> versions = (Collection<String>) versionsYaml.get("documentation");

        return new LinkedHashSet<>(versions);
    }

    private Guide createGuide(GitCloneDirectory cloneDirectory, String quarkusVersion, String type,
            Map<String, Object> parsedGuide,
            String summaryKey, Language language, Catalog messages) {
        String parsedUrl = toString(parsedGuide.get("url"));
        if (parsedUrl.startsWith("http")) {
            // we are looking at a quarkiverse guide:
            return createQuarkiverseGuide(quarkusVersion, type, parsedGuide, summaryKey);
        } else {
            return createCoreGuide(cloneDirectory, quarkusVersion, type, parsedGuide, summaryKey, language, messages);
        }
    }

    private Guide createCoreGuide(GitCloneDirectory cloneDirectory, String quarkusVersion, String type,
            Map<String, Object> parsedGuide,
            String summaryKey, Language language, Catalog messages) {
        Guide guide = new Guide();
        guide.quarkusVersion = quarkusVersion;
        guide.language = language;
        guide.origin = toString(parsedGuide.get("origin"));
        if (guide.origin == null) {
            guide.origin = QUARKUS_ORIGIN;
        }
        guide.type = type;
        guide.title.set(language, renderMarkdown(translate(messages, toString(parsedGuide.get("title")))));
        guide.summary.set(language, renderMarkdown(translate(messages, toString(parsedGuide.get(summaryKey)))));
        String parsedUrl = toString(parsedGuide.get("url"));
        guide.url = httpUrl(siteUris.get(language), quarkusVersion, parsedUrl);
        GitInputProvider gitInputProvider = new GitInputProvider(cloneDirectory.git(), cloneDirectory.pagesTree(),
                htmlPath(language, quarkusVersion, parsedUrl));
        guide.htmlFullContentProvider.set(language, gitInputProvider);

        if (!gitInputProvider.isFileAvailable()) {
            // if  a file is not present we do not want to add such guide. Since if the html is not there
            // it means that users won't be able to open it on the site, and returning it in the search results make it pointless.

            // Since this file was listed in the yaml of a rev from which the site was built the html should've been present,
            // but is missing for some reason that needs investigation:
            failureCollector.warning(
                    FailureCollector.Stage.TRANSLATION,
                    "Guide " + guide + " is ignored since we were not able to find an HTML content file for it.");
            return null;
        }

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

    record VersionAndPaths(String version, String versionDirectory, String path) {
    }

    record QuarkusIOCloneDirectory(VersionFilter versionFilter, GitCloneDirectory cloneDirectory) implements Closeable {
        public QuarkusIOCloneDirectory(FailureCollector failureCollector, GitCloneDirectory cloneDirectory) {
            this(VersionFilter.filter(cloneDirectory, failureCollector), cloneDirectory);
        }

        @Override
        public void close() throws IOException {
            try (var closer = new Closer<IOException>()) {
                closer.push(GitCloneDirectory::close, cloneDirectory);
            }
        }

        public Optional<String> unprocessed() {
            Collection<String> unprocessedVersions = versionFilter.unprocessedVersions();
            if (!unprocessedVersions.isEmpty()) {
                return Optional.of(
                        "Not all expected versions were discovered while parsing %s. Missing versions are: %s"
                                .formatted(cloneDirectory.details(), unprocessedVersions));
            }
            return Optional.empty();
        }
    }

    private static abstract class VersionFilter implements Predicate<VersionAndPaths> {

        private static VersionFilter filter(GitCloneDirectory cloneDirectory, FailureCollector failureCollector) {
            try (InputStream inputStream = cloneDirectory.sourcesFile("_data/versions.yaml")) {
                Set<String> versions = parseVersions(inputStream);
                return new CollectionFilter(versions);
            } catch (Exception e) {
                failureCollector.warning(FailureCollector.Stage.PARSING,
                        "Unable to find versions file with explicit list of versions to index within %s, resulting in including all discovered versions."
                                .formatted(cloneDirectory.toString()));
                return AcceptAllFilter.INSTANCE;

            }
        }

        public abstract Collection<String> unprocessedVersions();

        private static class CollectionFilter extends VersionFilter {

            private final Map<String, Boolean> versions;

            public CollectionFilter(Collection<String> versions) {
                this.versions = new HashMap<>(versions.size());
                for (String version : versions) {
                    this.versions.put(version, false);
                }
            }

            @Override
            public Collection<String> unprocessedVersions() {
                return versions.entrySet().stream()
                        .filter(Predicate.not(Map.Entry::getValue))
                        .map(Map.Entry::getKey)
                        .toList();
            }

            @Override
            public boolean test(VersionAndPaths version) {
                return Boolean.TRUE.equals(versions.compute(version.version(), (key, value) -> value == null ? null : true));
            }
        }

        private static class AcceptAllFilter extends VersionFilter {
            public static final VersionFilter INSTANCE = new AcceptAllFilter();

            @Override
            public boolean test(VersionAndPaths s) {
                return true;
            }

            @Override
            public Collection<String> unprocessedVersions() {
                return List.of();
            }
        }
    }
}
