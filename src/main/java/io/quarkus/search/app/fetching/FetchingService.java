package io.quarkus.search.app.fetching;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.quarkusio.QuarkusIO;
import io.quarkus.search.app.quarkusio.QuarkusIOConfig;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.FileUtils;
import io.quarkus.search.app.util.GitCloneDirectory;

import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;

import org.hibernate.search.util.common.impl.SuppressingCloser;

import org.apache.commons.io.function.IOBiFunction;
import org.eclipse.jgit.api.errors.GitAPIException;

@ApplicationScoped
public class FetchingService {

    @Inject
    QuarkusIOConfig quarkusIOConfig;

    public QuarkusIO fetchQuarkusIo() {
        return fetch(quarkusIOConfig.gitUri(),
                List.of(QuarkusIO.SOURCE_BRANCH, QuarkusIO.PAGES_BRANCH),
                sortMap(quarkusIOConfig.localized()),
                List.of(QuarkusIO.LOCALIZED_SOURCE_BRANCH, QuarkusIO.LOCALIZED_PAGES_BRANCH),
                (directory, localizedSites) -> new QuarkusIO(quarkusIOConfig, directory, localizedSites));
    }

    private Map<Language, QuarkusIOConfig.SiteConfig> sortMap(Map<String, QuarkusIOConfig.SiteConfig> localized) {
        Map<Language, QuarkusIOConfig.SiteConfig> map = new LinkedHashMap<>();
        for (String lang : localized.keySet().stream().sorted().toList()) {
            map.put(Language.fromString(lang), localized.get(lang));
        }
        return map;
    }

    private <T> T fetch(URI uri, List<String> branches,
            Map<Language, QuarkusIOConfig.SiteConfig> localized, List<String> localizedBranches,
            IOBiFunction<GitCloneDirectory, Map<Language, GitCloneDirectory>, T> function) {
        try {
            if (LaunchMode.DEVELOPMENT.equals(LaunchMode.current()) && isZip(uri)) {
                var zipPath = Path.of(uri);
                Log.warnf(
                        "Unzipping '%s': this application is most likely indexing only a sample of quarkus.io. See README to index the full website.",
                        uri);
                try (CloseableDirectory unzipped = CloseableDirectory.temp("quarkus.io-unzipped")) {
                    Path mainPath = unzipped.path().resolve("main-repository");
                    FileUtils.unzip(zipPath, mainPath);
                    uri = mainPath.toUri();

                    Map<Language, QuarkusIOConfig.SiteConfig> localizedUnzipped = new HashMap<>();

                    for (Map.Entry<Language, QuarkusIOConfig.SiteConfig> entry : localized.entrySet()) {
                        try {
                            URI configuredGitUri = entry.getValue().gitUri();
                            if (!isZip(configuredGitUri)) {
                                throw new IllegalArgumentException("Localized site git URI is not pointing to a zip archive.");
                            }
                            Log.warnf(
                                    "Unzipping '%s': this application is most likely indexing only a sample of localized %s.quarkus.io. See README to index the full website.",
                                    configuredGitUri, entry.getKey().code);
                            Path localizedPath = unzipped.path().resolve(entry.getKey().code + "-repository");
                            FileUtils.unzip(Path.of(configuredGitUri), localizedPath);

                            localizedUnzipped.put(entry.getKey(),
                                    new SiteConfigMock(localizedPath.toUri(), entry.getValue().webUri()));

                        } catch (RuntimeException | IOException e) {
                            throw new IllegalStateException("Failed to unzip '" + uri + "': " + e.getMessage(), e);
                        }
                    }

                    // While technically unnecessary (we could use the unzipped directory directly),
                    // this cloning ensures we run the same code in dev mode as in prod.
                    return gitClone(uri, branches, localizedUnzipped, localizedBranches, function);
                } catch (RuntimeException | IOException e) {
                    throw new IllegalStateException("Failed to unzip '" + uri + "': " + e.getMessage(), e);
                }
            } else {
                return gitClone(uri, branches, localized, localizedBranches, function);
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to fetch: " + e.getMessage(), e);
        }
    }

    private static boolean isZip(URI uri) {
        return uri.getScheme().equals("file")
                && uri.getPath().endsWith(".zip");
    }

    private <T> T gitClone(URI gitUri, List<String> branches,
            Map<Language, QuarkusIOConfig.SiteConfig> localized, List<String> localizedBranches,
            IOBiFunction<GitCloneDirectory, Map<Language, GitCloneDirectory>, T> function) {
        List<GitCloneDirectory> directories = new ArrayList<>();
        try {
            GitCloneDirectory mainRepository = GitCloneDirectory.mainRepository(gitUri, branches);
            directories.add(mainRepository);

            Map<Language, GitCloneDirectory> localizedSites = new LinkedHashMap<>();
            for (Map.Entry<Language, QuarkusIOConfig.SiteConfig> entry : localized.entrySet()) {
                URI localizedUri = entry.getValue().gitUri();
                GitCloneDirectory localizedRepository = GitCloneDirectory.localizedRepository(entry.getKey(), localizedUri,
                        localizedBranches);
                directories.add(localizedRepository);
                localizedSites.put(entry.getKey(), localizedRepository);
            }

            return function.apply(mainRepository, localizedSites);
        } catch (RuntimeException | IOException | GitAPIException e) {
            new SuppressingCloser(e)
                    .pushAll(directories);
            throw new IllegalStateException(
                    "Failed to clone git repository '%s'/%s: %s".formatted(
                            gitUri, localized.values().stream().map(QuarkusIOConfig.SiteConfig::gitUri).toList(),
                            e.getMessage()),
                    e);
        }
    }

    private record SiteConfigMock(URI gitUri, URI webUri) implements QuarkusIOConfig.SiteConfig {
    }
}
