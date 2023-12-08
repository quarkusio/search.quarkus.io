package io.quarkus.search.app.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkus.search.app.QuarkusVersions;
import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.quarkusio.QuarkusIO;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.FileUtils;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;

import org.hibernate.search.util.common.impl.Closer;
import org.hibernate.search.util.common.impl.SuppressingCloser;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.yaml.snakeyaml.Yaml;

/**
 * Helper to create a simulated quarkus.io repository to use for fetching in test/dev mode.
 */
public final class QuarkusIOSample {

    private QuarkusIOSample() {
    }

    public static final String SAMPLED_NON_LATEST_VERSION = "main";

    private static Path testResourcesSamplePath() {
        return Path.of(System.getProperty("maven.project.testResourceDirectory", "src/test/resources"))
                .toAbsolutePath()
                .resolve("quarkusio-sample.zip");
    }

    private static Path testResourcesSamplePath(Language language) {
        return Path.of(System.getProperty("maven.project.testResourceDirectory", "src/test/resources"))
                .toAbsolutePath()
                .resolve("quarkusio-sample-" + language.code + ".zip");
    }

    // Run this to update the bundle in src/test/resources.
    // Expects to be run with the project root as the current working directory.
    // Expects at least one argument: the path to your local clone of quarkus.io,
    // with the next arguments being interpreted as a pair of language code and localized site repository
    // Remotes to try to copy the data from are
    // "origin" and the local branch.
    public static void main(String[] args) throws IOException {
        if (args.length % 2 == 0) {
            throw new IllegalArgumentException("Expected at least 1 argument, got none."
                    + " Arguments must follow the pattern: main-repository [lang1 lang1-repository ... [langN langN-repository ]]."
                    + " Where language-specific repositories are optional and if provided must provide a language and its repository.");
        }
        Path originalPath = Path.of(args[0]);
        if (!Files.isDirectory(originalPath)) {
            throw new IllegalArgumentException(originalPath + " is not a directory");
        }
        List<String> remotes = Arrays.asList("upstream", "origin", null);

        try (CloseableDirectory copyRootDir = CloseableDirectory.temp("quarkusio-sample-building")) {
            copy(originalPath, remotes, copyRootDir.path(), new AllFilterDefinition(), QuarkusIO.PAGES_BRANCH,
                    QuarkusIO.SOURCE_BRANCH, true);
            Path sampleAbsolutePath = testResourcesSamplePath();

            Files.deleteIfExists(sampleAbsolutePath);
            FileUtils.zip(copyRootDir.path(), sampleAbsolutePath);
        }

        for (int i = 1; i < args.length; i += 2) {
            Language language = Language.fromString(args[i]);
            if (language == null) {
                throw new IllegalArgumentException(args[i] + " is not a supported language.");
            }
            Path originalLocalizedPath = Path.of(args[i + 1]);

            try (CloseableDirectory copyRootDir = CloseableDirectory.temp("quarkusio-sample-building")) {
                copy(originalLocalizedPath, remotes, copyRootDir.path(), new AllLocalizedFilterDefinition(language),
                        QuarkusIO.LOCALIZED_PAGES_BRANCH, QuarkusIO.LOCALIZED_SOURCE_BRANCH, false);
                Path sampleAbsolutePath = testResourcesSamplePath(language);

                Files.deleteIfExists(sampleAbsolutePath);
                FileUtils.zip(copyRootDir.path(), sampleAbsolutePath);
            }
        }
    }

    public static CloseableDirectory createFromTestResourcesSample(FilterDefinition filterDef) {
        return createFromTestResourcesSample(filterDef, testResourcesSamplePath(), QuarkusIO.PAGES_BRANCH,
                QuarkusIO.SOURCE_BRANCH, true);
    }

    public static CloseableDirectory createFromTestResourcesLocalizedSample(Language language, FilterDefinition filterDef) {
        return createFromTestResourcesSample(filterDef, testResourcesSamplePath(language), QuarkusIO.LOCALIZED_PAGES_BRANCH,
                QuarkusIO.LOCALIZED_SOURCE_BRANCH, false);
    }

    private static CloseableDirectory createFromTestResourcesSample(FilterDefinition filterDef, Path path, String pagesBranch,
            String sourceBranch, boolean failOnMissing) {
        CloseableDirectory copyRootDir = null;
        try (CloseableDirectory unzippedQuarkusIoSample = CloseableDirectory.temp("quarkusio-sample-unzipped")) {
            FileUtils.unzip(path, unzippedQuarkusIoSample.path());
            copyRootDir = CloseableDirectory.temp(filterDef.toString());
            copy(unzippedQuarkusIoSample.path(), Collections.singletonList(null),
                    copyRootDir.path(), filterDef, pagesBranch, sourceBranch, failOnMissing);
            return copyRootDir;
        } catch (RuntimeException | IOException e) {
            new SuppressingCloser(e).push(copyRootDir);
            throw new IllegalStateException(
                    "Couldn't create QuarkusIO sample with filter '%s': %s".formatted(filterDef, e.getMessage()), e);
        }
    }

    public static void copy(Path quarkusIoLocalPath, List<String> originalRemotes,
            Path copyRootPath, FilterDefinition filterDef, String pagesBranch, String sourceBranch, boolean failOnMissing) {
        try (Git originalGit = Git.open(quarkusIoLocalPath.toFile())) {
            GitTestUtils.cleanGitUserConfig();

            Repository originalRepo = originalGit.getRepository();

            var collector = new FilterDefinitionCollector();
            filterDef.define(collector);
            if (collector.sourceCopyPathToOriginalPath.isEmpty() && collector.pagesCopyPathToOriginalPath.isEmpty()) {
                throw new IllegalStateException("No path to copy");
            }

            try (Git copyGit = Git.init().setInitialBranch(pagesBranch)
                    .setDirectory(copyRootPath.toFile()).call()) {
                GitTestUtils.cleanGitUserConfig();

                RevCommit initialCommit = copyGit.commit().setMessage("Initial commit")
                        .setAllowEmpty(true)
                        .call();

                copyIfNecessary(quarkusIoLocalPath, originalRepo, originalRemotes, pagesBranch,
                        copyRootPath, copyGit,
                        collector.pagesCopyPathToOriginalPath, failOnMissing);

                copyGit.checkout()
                        .setName(sourceBranch)
                        .setCreateBranch(true)
                        .setStartPoint(initialCommit)
                        .call();
                copyIfNecessary(quarkusIoLocalPath, originalRepo, originalRemotes, sourceBranch,
                        copyRootPath, copyGit,
                        collector.sourceCopyPathToOriginalPath, failOnMissing);

                editIfNecessary(quarkusIoLocalPath,
                        copyRootPath, copyGit,
                        collector.yamlQuarkusFilesToFilter);

            }
        } catch (RuntimeException | IOException | GitAPIException e) {
            throw new IllegalStateException(
                    "Couldn't copy QuarkusIO from '%s' to '%s' with filter '%s': %s"
                            .formatted(quarkusIoLocalPath, copyRootPath, filterDef, e.getMessage()),
                    e);
        }
    }

    private static void copyIfNecessary(Path quarkusIoLocalPath, Repository originalRepo,
            List<String> originalRemotes, String originalBranch,
            Path copyRootPath, Git copyGit, Map<String, String> copyPathToOriginalPath, boolean failOnMissing)
            throws IOException, GitAPIException {
        if (copyPathToOriginalPath.isEmpty()) {
            return;
        }
        GitCopier copier = GitCopier.create(originalRepo,
                failOnMissing,
                // For convenience, we try multiple origins
                originalRemotes.stream()
                        .map(r -> r != null ? r + "/" + originalBranch : originalBranch)
                        .toArray(String[]::new));
        copier.copy(copyRootPath, copyPathToOriginalPath);
        copyGit.add().addFilepattern(".").call();
        copyGit.commit().setMessage("""
                Copying from %s

                Copies:%s"""
                .formatted(quarkusIoLocalPath,
                        copyPathToOriginalPath.entrySet().stream()
                                .map(e -> e.getValue() + " => " + e.getKey())
                                .collect(Collectors.joining("\n* ", "\n* ", "\n"))))
                .call();
    }

    private static void editIfNecessary(Path quarkusIoLocalPath, Path copyRootPath, Git copyGit,
            Map<String, Consumer<Path>> yamlQuarkusFilesToFilter)
            throws IOException, GitAPIException {
        if (yamlQuarkusFilesToFilter.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Consumer<Path>> entry : yamlQuarkusFilesToFilter.entrySet()) {
            entry.getValue().accept(copyRootPath.resolve(entry.getKey()));
        }

        copyGit.add().addFilepattern(".").call();
        copyGit.commit().setMessage("""
                Edit Quarkus metadata yaml files %s

                Edited:%s"""
                .formatted(
                        quarkusIoLocalPath,
                        yamlQuarkusFilesToFilter.values().stream()
                                .map(Object::toString)
                                .collect(Collectors.joining("\n* ", "\n* ", "\n"))))
                .call();
    }

    private static void yamlQuarkusEditor(Path fileToEdit, GuideRef[] refs) {
        yamlQuarkusEditor(fileToEdit, quarkusYaml -> {
            Set<String> guideRefs = Arrays.stream(refs).map(GuideRef::name).collect(Collectors.toSet());

            Map<String, Object> filtered = new HashMap<>();
            Map<String, List<Object>> guides = new HashMap<>();
            filtered.put("categories", quarkusYaml.get("categories"));
            filtered.put("types", guides);
            for (Map.Entry<String, List<Map<String, Object>>> entry : ((Map<String, List<Map<String, Object>>>) quarkusYaml
                    .get("types")).entrySet()) {
                for (Map<String, Object> guide : entry.getValue()) {
                    if (guideRefs.contains(guide.get("url"))) {
                        guides.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                .add(guide);
                    }
                }
            }
            return filtered;
        });
    }

    private static void yamlQuarkiverseEditor(Path fileToEdit) {
        yamlQuarkusEditor(fileToEdit, quarkusYaml -> {
            Set<String> guideRefs = Arrays.stream(GuideRef.quarkiverse()).map(GuideRef::name).collect(Collectors.toSet());

            Map<String, Object> filtered = new HashMap<>();
            Map<String, List<Object>> guides = new HashMap<>();
            filtered.put("types", guides);
            for (Map.Entry<String, List<Map<String, Object>>> entry : ((Map<String, List<Map<String, Object>>>) quarkusYaml
                    .get("types")).entrySet()) {
                for (Map<String, Object> guide : entry.getValue()) {
                    if (guideRefs.contains(guide.get("url"))) {
                        guides.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                .add(guide);
                    }
                }
            }
            return filtered;
        });
    }

    private static void yamlQuarkusEditor(Path fileToEdit, Function<Map<String, Object>, Map<String, Object>> editor) {
        Map<String, Object> filtered;
        try (InputStream inputStream = Files.newInputStream(fileToEdit)) {
            Yaml yaml = new Yaml();
            Map<String, Object> quarkusYaml = yaml.load(inputStream);
            filtered = editor.apply(quarkusYaml);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + fileToEdit, e);
        }

        try (OutputStream outputStream = Files.newOutputStream(fileToEdit);
                OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
            Yaml yaml = new Yaml();
            yaml.dump(filtered, writer);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save " + fileToEdit, e);
        }
    }

    public abstract static class FilterDefinition {
        private final String name;

        public FilterDefinition(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public abstract void define(FilterDefinitionCollector c);
    }

    public static class AllFilterDefinition extends AbstractGuideRefSetFilterDefinition {
        public AllFilterDefinition() {
            super("all", GuideRef.local());
        }
    }

    public static class SearchServiceFilterDefinition extends AbstractGuideRefSetFilterDefinition {
        private static final GuideRef[] GUIDES = new GuideRef[] {
                GuideRef.HIBERNATE_ORM,
                GuideRef.HIBERNATE_ORM_PANACHE,
                GuideRef.HIBERNATE_ORM_PANACHE_KOTLIN,
                GuideRef.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                GuideRef.HIBERNATE_REACTIVE,
                GuideRef.HIBERNATE_REACTIVE_PANACHE,
                GuideRef.SPRING_DATA_JPA,
                GuideRef.DUPLICATED_CONTEXT,
                GuideRef.SECURITY_OIDC_BEARER_TOKEN_AUTHENTICATION,
                GuideRef.STORK_REFERENCE
        };

        public static GuideRef[] guides() {
            return GUIDES;
        }

        public SearchServiceFilterDefinition() {
            super("search-service-subset", GUIDES);
        }
    }

    public static class SearchServiceSynonymsFilterDefinition extends AbstractGuideRefSetFilterDefinition {
        private static final GuideRef[] GUIDES = new GuideRef[] {
                GuideRef.RESTEASY_REACTIVE_REFERENCE,
                GuideRef.VERTX_REFERENCE,
                GuideRef.DEV_SERVICES_REFERENCE
        };

        public static GuideRef[] guides() {
            return GUIDES;
        }

        public SearchServiceSynonymsFilterDefinition() {
            super("search-service-synonyms-subset", GUIDES);
        }
    }

    private static abstract class AbstractGuideRefSetFilterDefinition extends FilterDefinition {
        private final GuideRef[] guides;

        protected AbstractGuideRefSetFilterDefinition(String name, GuideRef... guides) {
            super(name);
            this.guides = guides;
        }

        @Override
        public void define(FilterDefinitionCollector c) {
            c.addMetadata(QuarkusVersions.LATEST, guides);
            c.addMetadata(SAMPLED_NON_LATEST_VERSION, guides);
            c.addQuarkiverseMetadata(SAMPLED_NON_LATEST_VERSION);
            for (GuideRef guideRef : guides) {
                c.addGuide(guideRef);
                c.addGuide(guideRef, SAMPLED_NON_LATEST_VERSION);
            }
        }
    }

    public static class AllLocalizedFilterDefinition extends FilterDefinition {
        private final Language language;

        public AllLocalizedFilterDefinition(Language language) {
            super("all-localized-" + language.code);
            this.language = language;
        }

        @Override
        public void define(FilterDefinitionCollector c) {
            c.addLocalizedMetadata(language, QuarkusVersions.LATEST);
            c.addLocalizedMetadata(language, SAMPLED_NON_LATEST_VERSION);
            c.addLocalizedQuarkiverseMetadata(language, SAMPLED_NON_LATEST_VERSION);
            for (GuideRef guideRef : GuideRef.local()) {
                c.addLocalizedGuide(guideRef, QuarkusVersions.LATEST);
                c.addLocalizedGuide(guideRef, SAMPLED_NON_LATEST_VERSION);
            }
        }
    }

    public static class FilterDefinitionCollector {
        private final Map<String, String> sourceCopyPathToOriginalPath = new LinkedHashMap<>();
        private final Map<String, String> pagesCopyPathToOriginalPath = new LinkedHashMap<>();
        private final Map<String, Consumer<Path>> yamlQuarkusFilesToFilter = new LinkedHashMap<>();

        FilterDefinitionCollector() {
        }

        public FilterDefinitionCollector addGuide(GuideRef ref) {
            return addGuide(ref, QuarkusVersions.LATEST);
        }

        public FilterDefinitionCollector addGuide(GuideRef ref, String version) {
            String htmlPath = QuarkusIO.htmlPath(version, ref.name());
            htmlPath = htmlPath.startsWith("/") ? htmlPath.substring(1) : htmlPath;
            addOnPagesBranch(htmlPath, htmlPath);
            return this;
        }

        public FilterDefinitionCollector addMetadata(String version, GuideRef[] guides) {
            String metadataPath = QuarkusIO.yamlMetadataPath(version).toString();
            addOnSourceBranch(metadataPath, metadataPath);
            addMetadataToFilter(metadataPath, path -> yamlQuarkusEditor(path, guides));
            return this;
        }

        public FilterDefinitionCollector addQuarkiverseMetadata(String version) {
            String metadataPath = QuarkusIO.yamlQuarkiverseMetadataPath(version).toString();
            addOnSourceBranch(metadataPath, metadataPath);
            addMetadataToFilter(metadataPath, QuarkusIOSample::yamlQuarkiverseEditor);
            return this;
        }

        public FilterDefinitionCollector addLocalizedGuide(GuideRef ref, String version) {
            String htmlPath = QuarkusIO.htmlPath(version, ref.name());
            htmlPath = "docs" + (htmlPath.startsWith("/") ? "" : "/") + htmlPath;
            addOnPagesBranch(htmlPath, htmlPath);
            return this;
        }

        public FilterDefinitionCollector addLocalizedMetadata(Language language, String version) {
            return addLocalizedMetadata(language, version, "quarkus.yaml.po");
        }

        public FilterDefinitionCollector addLocalizedQuarkiverseMetadata(Language language, String version) {
            return addLocalizedMetadata(language, version, "quarkiverse.yaml.po");
        }

        private FilterDefinitionCollector addLocalizedMetadata(Language language, String version, String filename) {
            String metadataPath = Path.of("l10n", "po", language.locale, "_data", "versioned", version, "index", filename)
                    .toString();
            addOnSourceBranch(metadataPath, metadataPath);
            return this;
        }

        public void addOnSourceBranch(String originalPath, String copyPath) {
            add("source", sourceCopyPathToOriginalPath, originalPath, copyPath);
        }

        public void addOnPagesBranch(String originalPath, String copyPath) {
            add("pages", pagesCopyPathToOriginalPath, originalPath, copyPath);
        }

        public void addMetadataToFilter(String editPath, Consumer<Path> editor) {
            if (yamlQuarkusFilesToFilter.put(editPath, editor) != null) {
                throw new IllegalArgumentException("Editing the same file %s multiple times".formatted(editPath));
            }
        }

        private static void add(String branch, Map<String, String> copyPathToOriginalPath,
                String originalPath, String copyPath) {
            var previous = copyPathToOriginalPath.put(copyPath, originalPath);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Copying multiple original paths on branch %s to %s: %s, %s"
                                .formatted(branch, copyPath, previous, originalPath));
            }
        }
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @QuarkusTestResource(Resource.class)
    public @interface Setup {
        Class<? extends FilterDefinition> filter() default AllFilterDefinition.class;

        Class<? extends FilterDefinition> localizedFilter() default AllLocalizedFilterDefinition.class;
    }

    public static class Resource implements QuarkusTestResourceConfigurableLifecycleManager<Setup> {
        private FilterDefinition filterDef;
        private Map<Language, FilterDefinition> localizedFilterDef = new HashMap<>();
        private Set<CloseableDirectory> fixtures = new HashSet<>();

        @Override
        public void init(Setup annotation) {
            try {
                filterDef = annotation.filter().getConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Couldn't instantiate %s".formatted(annotation.filter()), e);
            }
            try {
                Constructor<? extends FilterDefinition> constructor = annotation.localizedFilter()
                        .getConstructor(Language.class);
                for (Language language : Language.nonDefault) {
                    localizedFilterDef.put(language, constructor.newInstance(language));
                }
            } catch (Exception e) {
                throw new RuntimeException("Couldn't instantiate %s".formatted(annotation.filter()), e);
            }
        }

        @Override
        public Map<String, String> start() {
            Map<String, String> settings = new HashMap<>();
            CloseableDirectory main = QuarkusIOSample.createFromTestResourcesSample(filterDef);
            fixtures.add(main);
            settings.put("quarkusio.git-uri", main.path().toUri().toString());

            for (Map.Entry<Language, FilterDefinition> entry : localizedFilterDef.entrySet()) {
                Language language = entry.getKey();
                CloseableDirectory directory = QuarkusIOSample.createFromTestResourcesLocalizedSample(language,
                        localizedFilterDef.get(language));
                fixtures.add(directory);
                settings.put("quarkusio.localized." + language.code + ".git-uri", directory.path().toUri().toString());
            }
            return settings;
        }

        @Override
        public void stop() {
            try (Closer<IOException> closer = new Closer<>()) {
                closer.pushAll(CloseableDirectory::close, fixtures);
                fixtures.clear();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
