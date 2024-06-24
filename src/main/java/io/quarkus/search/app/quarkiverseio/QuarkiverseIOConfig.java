package io.quarkus.search.app.quarkiverseio;

import java.net.URI;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkiverseio")
public interface QuarkiverseIOConfig {
    String WEB_URI_DEFAULT_STRING = "https://docs.quarkiverse.io/index/explore/index.html";
    URI WEB_URI_DEFAULT = URI.create(WEB_URI_DEFAULT_STRING);

    @WithDefault(WEB_URI_DEFAULT_STRING)
    URI webUri();

    @WithDefault("true")
    boolean enabled();
}
