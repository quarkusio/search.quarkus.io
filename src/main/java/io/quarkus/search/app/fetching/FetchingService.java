package io.quarkus.search.app.fetching;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.commons.io.function.IOBiFunction;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;

import org.hibernate.search.util.common.impl.SuppressingCloser;

import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.FileUtils;

@ApplicationScoped
public class FetchingService {

    @Inject
    FetchingConfig fetchingConfig;

    public QuarkusIO fetchQuarkusIo() {
        return fetch("quarkus.io", fetchingConfig.quarkusio(),
                List.of(QuarkusIO.SOURCE_BRANCH, QuarkusIO.PAGES_BRANCH),
                QuarkusIO::new);
    }

    private <T> T fetch(String name, FetchingConfig.Source source, List<String> branches,
            IOBiFunction<CloseableDirectory, Git, T> function) {
        try {
            var uri = source.uri();
            if (LaunchMode.DEVELOPMENT.equals(LaunchMode.current())
                    && uri.getScheme().equals("file")
                    && uri.getPath().endsWith(".zip")) {
                var zipPath = Path.of(uri);
                Log.warn(
                        "Unzipping '%s': this application is most likely indexing only a sample of quarkus.io. See README to index the full website.");
                try (CloseableDirectory unzipped = CloseableDirectory.temp(name + "-unzipped")) {
                    FileUtils.unzip(zipPath, unzipped.path());
                    uri = unzipped.path().toUri();
                    // While technically unnecessary (we could use the unzipped directory directly),
                    // this cloning ensures we run the same code in dev mode as in prod.
                    return gitClone(name, uri, branches, function);
                } catch (RuntimeException | IOException e) {
                    throw new IllegalStateException("Failed to unzip '" + uri + "': " + e.getMessage(), e);
                }
            } else {
                return gitClone(name, uri, branches, function);
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to fetch '" + name + "': " + e.getMessage(), e);
        }
    }

    private <T> T gitClone(String name, URI gitUri, List<String> branches,
            IOBiFunction<CloseableDirectory, Git, T> function) {
        Log.infof("Fetching %s from %s.", name, gitUri);
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
            return function.apply(directory, git);
        } catch (RuntimeException | IOException | GitAPIException e) {
            new SuppressingCloser(e)
                    .push(git)
                    .push(directory);
            throw new IllegalStateException(
                    "Failed to clone git repository '" + gitUri + "': " + e.getMessage(), e);
        }
    }
}
