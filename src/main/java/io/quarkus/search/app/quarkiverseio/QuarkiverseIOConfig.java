package io.quarkus.search.app.quarkiverseio;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkiverseio")
public interface QuarkiverseIOConfig {

    Optional<Zip> zip();

    Optional<GithubArtifact> githubArtifact();

    @WithDefault("true")
    boolean enabled();

    @WithDefault("https://docs.quarkiverse.io/")
    URI baseUri();

    interface GithubArtifact {
        String token();

        String repository();

        //@WithDefault( "Publish website" )
        String actionName();

        //@WithDefault( "github-pages" )
        String artifactName();
    }

    interface Zip {
        Path path();
    }

}
