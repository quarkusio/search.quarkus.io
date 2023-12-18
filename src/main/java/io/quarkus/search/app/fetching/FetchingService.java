package io.quarkus.search.app.fetching;

import static io.quarkus.search.app.util.FileUtils.unzip;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.quarkusio.QuarkusIO;
import io.quarkus.search.app.quarkusio.QuarkusIOConfig;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.GitCloneDirectory;

import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;

import org.hibernate.search.util.common.impl.SuppressingCloser;

import org.apache.commons.io.function.IOBiFunction;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;

@ApplicationScoped
public class FetchingService {

    @Inject
    QuarkusIOConfig quarkusIOConfig;

    public QuarkusIO fetchQuarkusIo() {
        GitCloneDirectory main = null;
        Map<Language, GitCloneDirectory> localized = new LinkedHashMap<>();
        try (CloseableDirectory unzipDir = LaunchMode.DEVELOPMENT.equals(LaunchMode.current())
                ? CloseableDirectory.temp("quarkus.io-unzipped")
                : null) {
            main = fetchQuarkusIoSite("quarkus.io", quarkusIOConfig.gitUri(),
                    QuarkusIO.SOURCE_BRANCH, QuarkusIO.PAGES_BRANCH, unzipDir);
            for (Map.Entry<Language, QuarkusIOConfig.SiteConfig> entry : sortMap(quarkusIOConfig.localized()).entrySet()) {
                var language = entry.getKey();
                var config = entry.getValue();
                localized.put(language,
                        fetchQuarkusIoSite(language.code + ".quarkus.io", config.gitUri(),
                                QuarkusIO.LOCALIZED_SOURCE_BRANCH, QuarkusIO.LOCALIZED_PAGES_BRANCH, unzipDir));
            }
            return new QuarkusIO(quarkusIOConfig, main, localized);
        } catch (RuntimeException | IOException e) {
            new SuppressingCloser(e)
                    .push(main)
                    .pushAll(localized.values());
            throw new IllegalStateException("Failed to fetch quarkus.io: " + e.getMessage(), e);
        }
    }

    private GitCloneDirectory fetchQuarkusIoSite(String siteName, URI gitUri, String sourceBranch, String pagesBranch,
            CloseableDirectory unzipDir) {
        try {
            if (LaunchMode.DEVELOPMENT.equals(LaunchMode.current()) && isZip(gitUri)) {
                Log.warnf("Unzipping '%s': this application is most likely indexing only a sample of %s."
                        + " See README to index the full website.",
                        gitUri, siteName);
                Path unzippedPath = unzipDir.path().resolve(siteName);
                unzip(Path.of(gitUri), unzippedPath);
                gitUri = unzippedPath.toUri();
                // Fall-through and clone the directory.
                // While technically unnecessary (we could use the unzipped directory directly),
                // this cloning ensures we run the same code in dev mode as in prod.
            }
            return gitClone(siteName, gitUri, List.of(sourceBranch, pagesBranch),
                    (git, directory) -> new GitCloneDirectory(git, directory, pagesBranch));
        } catch (RuntimeException | IOException e) {
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

    private static boolean isZip(URI uri) {
        return uri.getScheme().equals("file")
                && uri.getPath().endsWith(".zip");
    }

    private <T> T gitClone(String name, URI gitUri, List<String> branches,
            IOBiFunction<Git, CloseableDirectory, T> function) {
        Log.infof("Cloning '%s' from '%s'.", name, gitUri);
        CloseableDirectory directory = null;
        Git git = null;
        try {
            directory = CloseableDirectory.temp(name);
            git = Git.cloneRepository()
                    .setURI(gitUri.toString())
                    .setDirectory(directory.path().toFile())
                    .setDepth(1)
                    .setNoTags()
                    .setBranch(branches.get(0))
                    .setBranchesToClone(branches.stream().map(b -> "refs/heads/" + b).toList())
                    .setProgressMonitor(new TextProgressMonitor())
                    // Unfortunately sparse checkouts are not supported: https://www.eclipse.org/forums/index.php/t/1094825/
                    .call();
            return function.apply(git, directory);
        } catch (RuntimeException | IOException | GitAPIException e) {
            new SuppressingCloser(e)
                    .push(git)
                    .push(directory);
            throw new IllegalStateException(
                    "Failed to clone git repository '%s' from '%s': %s".formatted(name, gitUri, e.getMessage()), e);
        }
    }
}
