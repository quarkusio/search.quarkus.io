package io.quarkus.search.app.fetching;

import static io.quarkus.search.app.util.FileUtils.unzip;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.indexing.FailureCollector;
import io.quarkus.search.app.quarkiverseio.QuarkiverseIO;
import io.quarkus.search.app.quarkiverseio.QuarkiverseIOConfig;
import io.quarkus.search.app.quarkusio.QuarkusIO;
import io.quarkus.search.app.quarkusio.QuarkusIOConfig;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.GitCloneDirectory;
import io.quarkus.search.app.util.SimpleExecutor;

import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;

import org.hibernate.search.util.common.impl.Closer;
import org.hibernate.search.util.common.impl.SuppressingCloser;

import io.vertx.core.impl.ConcurrentHashSet;

@ApplicationScoped
public class FetchingService {

    @Inject
    FetchingConfig fetchingConfig;

    @Inject
    QuarkusIOConfig quarkusIOConfig;

    @Inject
    QuarkiverseIOConfig quarkiverseIOConfig;

    private final Map<URI, GitCloneDirectory.Details> detailsCache = new ConcurrentHashMap<>();
    private final Set<CloseableDirectory> tempDirectories = new ConcurrentHashSet<>();

    public QuarkiverseIO fetchQuarkiverseIo(FailureCollector failureCollector) {
        return new QuarkiverseIO(quarkiverseIOConfig, failureCollector);
    }

    public QuarkusIO fetchQuarkusIo(FailureCollector failureCollector) {
        CompletableFuture<GitCloneDirectory> main = null;
        Map<Language, CompletableFuture<GitCloneDirectory>> localized = new LinkedHashMap<>();
        try (SimpleExecutor executor = new SimpleExecutor(fetchingConfig.parallelism())) {
            main = executor.submit(() -> fetchQuarkusIoSite("quarkus.io", quarkusIOConfig.gitUri(), QuarkusIO.MAIN_BRANCHES));
            for (Map.Entry<Language, QuarkusIOConfig.SiteConfig> entry : sortMap(quarkusIOConfig.localized()).entrySet()) {
                var language = entry.getKey();
                var config = entry.getValue();
                localized.put(language,
                        executor.submit(() -> fetchQuarkusIoSite(language.code + ".quarkus.io", config.gitUri(),
                                QuarkusIO.LOCALIZED_BRANCHES)));
            }
            executor.waitForSuccessOrThrow(fetchingConfig.timeout());
            // If we get here, all tasks succeeded.
            GitCloneDirectory mainRepository = main.join();
            return new QuarkusIO(quarkusIOConfig, mainRepository,
                    localized.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().join().root(mainRepository))),
                    failureCollector);
        } catch (RuntimeException | IOException e) {
            new SuppressingCloser(e)
                    .push(main, CompletableFuture::join)
                    .pushAll(localized.values(), CompletableFuture::join);
            throw new IllegalStateException("Failed to fetch quarkus.io: " + e.getMessage(), e);
        }
    }

    private GitCloneDirectory fetchQuarkusIoSite(String siteName, URI gitUri, GitCloneDirectory.Branches branches) {
        CloseableDirectory tempDir = null;
        GitCloneDirectory cloneDir = null;
        try {
            GitCloneDirectory.Details details = detailsCache.get(gitUri);
            if (details != null) {
                return details.openAndUpdate();
            }

            if (LaunchMode.DEVELOPMENT.equals(LaunchMode.current())) {
                if (isZip(gitUri)) {
                    Log.warnf("Unzipping '%s': this application is most likely indexing only a sample of %s."
                            + " See README to index the full website.",
                            gitUri, siteName);
                    tempDir = CloseableDirectory.temp(siteName);
                    tempDirectories.add(tempDir);
                    unzip(Path.of(gitUri), tempDir.path());
                    cloneDir = GitCloneDirectory.openAndUpdate(tempDir.path(), branches);
                } else if (isFile(gitUri)) {
                    Log.infof("Using the git repository '%s' as-is without cloning to speed up indexing of %s.",
                            gitUri, siteName);
                    // In dev mode, we want to skip cloning when possible, to make things quicker.
                    cloneDir = GitCloneDirectory.openAndUpdate(Path.of(gitUri), branches);
                }
            }

            if (cloneDir == null) {
                // We always end up here in prod and tests.
                // That's fine, because prod will always use remote (http/git) git URIs anyway,
                // never local ones (file).
                // We may skip it in dev mode though.
                tempDir = CloseableDirectory.temp(siteName);
                tempDirectories.add(tempDir);
                cloneDir = GitCloneDirectory.clone(gitUri, tempDir.path(), branches);
            }

            detailsCache.put(gitUri, cloneDir.details());

            return cloneDir;
        } catch (RuntimeException | IOException e) {
            new SuppressingCloser(e).push(tempDir).push(cloneDir);
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
            closer.pushAll(CloseableDirectory::close, tempDirectories);
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException(
                    "Failed to close directories '%s': %s".formatted(tempDirectories, e.getMessage()), e);
        }
    }

}
