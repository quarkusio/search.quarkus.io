package io.quarkus.search.app.util;

import java.io.IOException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.kohsuke.github.GitHub;

import io.quarkiverse.githubapp.GitHubClientProvider;

/**
 * Provides GitHub clients authenticated as an installation of our GitHub App.
 */
@ApplicationScoped
public class GitHubClients {

    @Inject
    GitHubClientProvider clientProvider;

    /**
     * @param repository The full name of a repository, e.g. {@code quarkusio/search.quarkus.io}.
     * @return A client authenticated as the installation of the GitHub App on the given repository.
     * @throws IOException If the installation cannot be found or the GitHub API cannot be reached.
     */
    public GitHub forRepository(String repository) throws IOException {
        String[] parts = repository.split("/", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid GitHub repository name '" + repository
                    + "': expected format is <owner>/<name>");
        }
        long installationId = clientProvider.getApplicationClient().getApp()
                .getInstallationByRepository(parts[0], parts[1]).getId();
        return clientProvider.getInstallationClient(installationId);
    }
}
