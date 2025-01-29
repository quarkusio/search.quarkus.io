package io.quarkus.search.app.quarkiverseio;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkiverseio")
public interface QuarkiverseIOConfig {

    @WithDefault("github-artifact")
    SourceType source();

    enum SourceType {
        NONE,
        GITHUB_ARTIFACT,
        ZIP
    }

    Zip zip();

    GithubArtifact githubArtifact();

    @WithDefault("https://docs.quarkiverse.io/")
    URI baseUri();

    interface GithubArtifact {
        // Only necessary if source = github-artifact
        Optional<String> token();

        @WithDefault("quarkiverse/quarkiverse-docs")
        String repository();

        @WithDefault("Publish website")
        String actionName();

        @WithDefault("github-pages")
        String artifactName();
    }

    interface Zip {
        // Only necessary if source = zip
        Optional<Path> path();
    }

}
