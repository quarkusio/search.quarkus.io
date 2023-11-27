package io.quarkus.search.app.quarkusio;

import java.net.URI;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkusio")
public interface QuarkusIOConfig {
    URI gitUri();
}
