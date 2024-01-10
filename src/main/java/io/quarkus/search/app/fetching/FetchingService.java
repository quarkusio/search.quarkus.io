package io.quarkus.search.app.fetching;

import static io.quarkus.search.app.util.FileUtils.unzip;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.quarkusio.QuarkusIO;
import io.quarkus.search.app.quarkusio.QuarkusIOConfig;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.GitCloneDirectory;
import io.quarkus.search.app.util.SimpleExecutor;

import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;

import org.hibernate.search.util.common.impl.Closer;
import org.hibernate.search.util.common.impl.SuppressingCloser;

@ApplicationScoped
public class FetchingService {
    private static final Branches MAIN = new Branches(QuarkusIO.SOURCE_BRANCH, QuarkusIO.PAGES_BRANCH);
    private static final Branches LOCALIZED = new Branches(QuarkusIO.LOCALIZED_SOURCE_BRANCH, QuarkusIO.LOCALIZED_PAGES_BRANCH);

    @Inject
    FetchingConfig fetchingConfig;

    @Inject
    QuarkusIOConfig quarkusIOConfig;

    private final Map<URI, GitCloneDirectory.GitDirectoryDetails> repositories = new HashMap<>();
    private final Set<CloseableDirectory> closeableDirectories = new HashSet<>();

    public QuarkusIO fetchQuarkusIo() {
        CompletableFuture<GitCloneDirectory> main = null;
        Map<Language, CompletableFuture<GitCloneDirectory>> localized = new LinkedHashMap<>();
        try (CloseableDirectory unzipDir = LaunchMode.DEVELOPMENT.equals(LaunchMode.current())
                ? CloseableDirectory.temp("quarkus.io-unzipped")
                : null;
                SimpleExecutor executor = new SimpleExecutor(fetchingConfig.parallelism())) {
            main = executor.submit(() -> fetchQuarkusIoSite("quarkus.io", quarkusIOConfig.gitUri(), MAIN, unzipDir));
            for (Map.Entry<Language, QuarkusIOConfig.SiteConfig> entry : sortMap(quarkusIOConfig.localized()).entrySet()) {
                var language = entry.getKey();
                var config = entry.getValue();
                localized.put(language,
                        executor.submit(
                                () -> fetchQuarkusIoSite(language.code + ".quarkus.io", config.gitUri(), LOCALIZED, unzipDir)));
            }
            executor.waitForSuccessOrThrow(fetchingConfig.timeout());
            // If we get here, all tasks succeeded.
            return new QuarkusIO(quarkusIOConfig, main.join(),
                    localized.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().join())));
        } catch (RuntimeException | IOException e) {
            new SuppressingCloser(e)
                    .push(main, CompletableFuture::join)
                    .pushAll(localized.values(), CompletableFuture::join);
            throw new IllegalStateException("Failed to fetch quarkus.io: " + e.getMessage(), e);
        }
    }

    private GitCloneDirectory fetchQuarkusIoSite(String siteName, URI gitUri, Branches branches,
            CloseableDirectory unzipDir) {
        // We only want to clean up a clone directory if it is a clone from a remote repository or an unzipped one.
        //  If we got a local repository we want to keep it as is and no cloning is needed.
        //  If it was a local unzipped version -- the "cloneDir" will be removed sooner than we are done with indexing,
        //  so we want to "clone" that directory, and then it'll be removed as if a remote repository clone.
        boolean requiresCloning = !isFile(gitUri) || isZip(gitUri);
        URI requestedGitUri = gitUri;
        CloseableDirectory cloneDir = null;
        try {
            GitCloneDirectory.GitDirectoryDetails repository = repositories.get(gitUri);

            if (LaunchMode.DEVELOPMENT.equals(LaunchMode.current()) && isZip(gitUri)) {
                if (repository != null) {
                    // We are working with a zip file, so we have nothing to refresh as there's no actual remote available;
                    //   just return the same repository without any changes:
                    return repository.open(branches.sources());
                }

                Log.warnf("Unzipping '%s': this application is most likely indexing only a sample of %s."
                        + " See README to index the full website.",
                        gitUri, siteName);
                Path unzippedPath = unzipDir.path().resolve(siteName);
                unzip(Path.of(gitUri), unzippedPath);
                gitUri = unzippedPath.toUri();
                // Fall-through and clone the directory.
                // This cloning is as if we are cloning a remote repository.
            } else {
                if (repository != null) {
                    // It's not a zip, so it may be either a local directory or a remote repository
                    // let's pull the changes from it as it shouldn't cause any exceptions, as a remote is actually out there.
                    return repository.pull(branches);
                }
            }

            cloneDir = requiresCloning
                    ? CloseableDirectory.temp(siteName)
                    : CloseableDirectory.of(Paths.get(gitUri));

            closeableDirectories.add(cloneDir);

            repository = new GitCloneDirectory.GitDirectoryDetails(cloneDir.path(), branches.pages());
            repositories.put(requestedGitUri, repository);

            // If we have a local repository -- open it, and then pull the changes, clone it otherwise:
            return requiresCloning ? repository.clone(gitUri, branches) : repository.open(branches.sources()).update(branches);
        } catch (RuntimeException | IOException e) {
            new SuppressingCloser(e).push(cloneDir);
            throw new IllegalStateException("Failed to fetch '%s': %s".formatted(siteName, e.getMessage()), e);
        }
    }

    private Map<Language, QuarkusIOConfig.SiteConfig> sortMap(Map<String, QuarkusIOConfig.SiteConfig> localized) {
        Map<Language, QuarkusIOConfig.SiteConfig> map = new LinkedHashMap<>();
        for (String lang : localized.keySet().stream().sorted().toList()) {
            map.put(Language.fromString(lang), localized.get(lang));
        }
        return map;
    }

    private static boolean isFile(URI uri) {
        return "file".equals(uri.getScheme());
    }

    private static boolean isZip(URI uri) {
        return isFile(uri) && uri.getPath().endsWith(".zip");
    }

    @PreDestroy
    public void cleanupTemporaryFolders() {
        try (Closer<IOException> closer = new Closer<>()) {
            closer.pushAll(CloseableDirectory::close, closeableDirectories);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to close directories '%s': %s".formatted(closeableDirectories, e.getMessage()), e);
        }
    }

    public record Branches(String sources, String pages) {
        public List<String> asRefList() {
            return List.of("refs/heads/" + sources, "refs/heads/" + pages);
        }

        public String[] asRefArray() {
            return asRefList().toArray(String[]::new);
        }
    }
}
