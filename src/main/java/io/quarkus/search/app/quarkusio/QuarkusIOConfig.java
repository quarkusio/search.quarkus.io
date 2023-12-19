package io.quarkus.search.app.quarkusio;

import java.net.URI;
import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocSection;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkusio")
public interface QuarkusIOConfig {
    String WEB_URI_DEFAULT_STRING = "https://quarkus.io";
    URI WEB_URI_DEFAULT = URI.create(WEB_URI_DEFAULT_STRING);

    URI gitUri();

    @WithDefault(WEB_URI_DEFAULT_STRING)
    URI webUri();

    @ConfigDocSection
    Map<String, SiteConfig> localized();

    interface SiteConfig {
        URI gitUri();

        URI webUri();
    }
}
