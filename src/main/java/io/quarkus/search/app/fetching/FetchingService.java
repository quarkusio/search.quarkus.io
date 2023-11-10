package io.quarkus.search.app.fetching;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.search.app.util.CloseableDirectory;
import io.quarkus.search.app.util.FileUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;

import org.hibernate.search.util.common.impl.SuppressingCloser;

@ApplicationScoped
public class FetchingService {

    @Inject
    FetchingConfig fetchingConfig;

    public QuarkusIO fetchQuarkusIo() {
        return new QuarkusIO(fetch("quarkus.io", fetchingConfig.quarkusio()));
    }

    private CloseableDirectory fetch(String name, FetchingConfig.Source source) {
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
                    return gitClone(name, uri);
                } catch (RuntimeException | IOException e) {
                    throw new IllegalStateException("Failed to unzip '" + uri + "': " + e.getMessage(), e);
                }
            } else {
                return gitClone(name, uri);
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to fetch '" + name + "': " + e.getMessage(), e);
        }
    }

    private CloseableDirectory gitClone(String name, URI gitUri) {
        Log.infof("Fetching %s from %s.", name, gitUri);
        CloseableDirectory clone = null;
        try {
            clone = CloseableDirectory.temp(name);
            try (Git git = Git.cloneRepository()
                    .setURI(gitUri.toString())
                    .setDirectory(clone.path().toFile())
                    .setDepth(1)
                    .setProgressMonitor(new TextProgressMonitor())
                    // Unfortunately sparse checkouts are not supported: https://www.eclipse.org/forums/index.php/t/1094825/
                    .call()) {
                return clone;
            }
        } catch (RuntimeException | IOException | GitAPIException e) {
            new SuppressingCloser(e).push(clone);
            throw new IllegalStateException(
                    "Failed to clone git repository '" + gitUri + "': " + e.getMessage(), e);
        }
    }
}
