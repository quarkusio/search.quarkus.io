package io.quarkus.search.app.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.search.app.fetching.QuarkusIO;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import org.hibernate.search.util.common.impl.SuppressingCloser;

import io.quarkus.search.app.QuarkusVersions;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.FileUtils;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;
import org.eclipse.jgit.revwalk.RevCommit;

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

    // Run this to update the bundle in src/test/resources.
    // Expects to be run with the project root as the current working directory.
    // Expects at least one argument: the path to your local clone of quarkus.io,
    // with the next arguments being interpreted as remotes to try to copy the data from
    // ("origin" and the local branch will still be used as the last resort)
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected at least 1 argument, got none");
        }
        Path originalPath = Path.of(args[0]);
        if (!Files.isDirectory(originalPath)) {
            throw new IllegalArgumentException(originalPath + " is not a directory");
        }
        List<String> remotes = Stream.concat(
                Arrays.stream(args).skip(1),
                Stream.of("upstream", "origin", null)).toList();

        try (CloseableDirectory copyRootDir = CloseableDirectory.temp("quarkusio-sample-building")) {
            copy(originalPath, remotes, copyRootDir.path(), new AllFilterDefinition());

            Path sampleAbsolutePath = testResourcesSamplePath();
            Files.deleteIfExists(sampleAbsolutePath);
            FileUtils.zip(copyRootDir.path(), sampleAbsolutePath);
        }
    }

    public static CloseableDirectory createFromTestResourcesSample(FilterDefinition filterDef) {
        CloseableDirectory copyRootDir = null;
        try (CloseableDirectory unzippedQuarkusIoSample = CloseableDirectory.temp("quarkusio-sample-unzipped")) {
            FileUtils.unzip(testResourcesSamplePath(), unzippedQuarkusIoSample.path());
            copyRootDir = CloseableDirectory.temp(filterDef.toString());
            copy(unzippedQuarkusIoSample.path(), Collections.singletonList(null),
                    copyRootDir.path(), filterDef);
            return copyRootDir;
        } catch (RuntimeException | IOException e) {
            new SuppressingCloser(e).push(copyRootDir);
            throw new IllegalStateException(
                    "Couldn't create QuarkusIO sample with filter '%s': %s".formatted(filterDef, e.getMessage()), e);
        }
    }

    public static void copy(Path quarkusIoLocalPath, List<String> originalRemotes,
            Path copyRootPath, FilterDefinition filterDef) {
        try (Git originalGit = Git.open(quarkusIoLocalPath.toFile())) {
            GitTestUtils.cleanGitUserConfig();

            Repository originalRepo = originalGit.getRepository();

            var collector = new FilterDefinitionCollector();
            filterDef.define(collector);
            if (collector.sourceCopyPathToOriginalPath.isEmpty() && collector.pagesCopyPathToOriginalPath.isEmpty()) {
                throw new IllegalStateException("No path to copy");
            }

            try (Git copyGit = Git.init().setInitialBranch(QuarkusIO.PAGES_BRANCH)
                    .setDirectory(copyRootPath.toFile()).call()) {
                GitTestUtils.cleanGitUserConfig();

                RevCommit initialCommit = copyGit.commit().setMessage("Initial commit")
                        .setAllowEmpty(true)
                        .call();

                copyIfNecessary(quarkusIoLocalPath, originalRepo, originalRemotes, QuarkusIO.PAGES_BRANCH,
                        copyRootPath, copyGit,
                        collector.pagesCopyPathToOriginalPath);

                copyGit.checkout()
                        .setName(QuarkusIO.SOURCE_BRANCH)
                        .setCreateBranch(true)
                        .setStartPoint(initialCommit)
                        .call();
                copyIfNecessary(quarkusIoLocalPath, originalRepo, originalRemotes, QuarkusIO.SOURCE_BRANCH,
                        copyRootPath, copyGit,
                        collector.sourceCopyPathToOriginalPath);

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
            Path copyRootPath, Git copyGit, Map<String, String> copyPathToOriginalPath)
            throws IOException, GitAPIException {
        if (copyPathToOriginalPath.isEmpty()) {
            return;
        }
        GitCopier copier = GitCopier.create(originalRepo,
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

    public static class AllFilterDefinition extends FilterDefinition {
        public AllFilterDefinition() {
            super("all");
        }

        @Override
        public void define(FilterDefinitionCollector c) {
            for (GuideRef guideRef : GuideRef.all()) {
                c.addGuide(guideRef);
                c.addGuide(guideRef, SAMPLED_NON_LATEST_VERSION);
            }
        }
    }

    public static class FilterDefinitionCollector {
        private final Map<String, String> sourceCopyPathToOriginalPath = new LinkedHashMap<>();
        private final Map<String, String> pagesCopyPathToOriginalPath = new LinkedHashMap<>();

        FilterDefinitionCollector() {
        }

        public FilterDefinitionCollector addGuide(GuideRef ref) {
            return addGuide(ref, QuarkusVersions.LATEST);
        }

        public FilterDefinitionCollector addGuide(GuideRef ref, String version) {
            String asciidocPath = QuarkusIO.asciidocPath(version, ref.name());
            addOnSourceBranch(asciidocPath, asciidocPath);
            String htmlPath = QuarkusIO.htmlPath(version, ref.name());
            addOnPagesBranch(htmlPath, htmlPath);
            return this;
        }

        public void addOnSourceBranch(String originalPath, String copyPath) {
            add("source", sourceCopyPathToOriginalPath, originalPath, copyPath);
        }

        public void addOnPagesBranch(String originalPath, String copyPath) {
            add("pages", pagesCopyPathToOriginalPath, originalPath, copyPath);
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
    }

    public static class Resource implements QuarkusTestResourceConfigurableLifecycleManager<Setup> {
        private FilterDefinition filterDef;
        private CloseableDirectory fixture;

        @Override
        public void init(Setup annotation) {
            try {
                filterDef = annotation.filter().getConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Couldn't instantiate %s".formatted(annotation.filter()), e);
            }
        }

        @Override
        public Map<String, String> start() {
            fixture = QuarkusIOSample.createFromTestResourcesSample(filterDef);
            return Map.of("fetching.quarkusio.uri", fixture.path().toUri().toString());
        }

        @Override
        public void stop() {
            if (fixture != null) {
                try {
                    fixture.close();
                    fixture = null;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}
