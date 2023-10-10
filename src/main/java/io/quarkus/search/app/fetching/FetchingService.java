package io.quarkus.search.app.fetching;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import org.hibernate.search.util.common.impl.SuppressingCloser;

@ApplicationScoped
public class FetchingService {

    @Inject
    FetchingConfig fetchingConfig;

    public QuarkusIO fetchQuarkusIo() {
        return new QuarkusIO(fetch("quarkus.io", fetchingConfig.quarkusio()));
    }

    private FetchedDirectory fetch(String name, FetchingConfig.Source source) {
        try {
            Log.infof("Fetching %s using method %s from %s.", name, source.method(), source.uri());
            return switch (source.method()) {
                case GIT -> gitClone(source.uri());
                case LOCAL -> FetchedDirectory.of(Path.of(source.uri()));
            };
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to fetch '" + name + "': " + e.getMessage(), e);
        }
    }

    private FetchedDirectory gitClone(URI gitUri) {
        FetchedDirectory clone = null;
        try {
            clone = FetchedDirectory.temp();
            try (Git git = Git.cloneRepository()
                    .setURI(gitUri.toString())
                    .setDirectory(clone.path().toFile())
                    .setDepth(1)
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
