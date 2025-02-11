package io.quarkus.search.app.ui;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "search.ui")
public interface SearchUiConfig {

    @WithDefault("true")
    boolean enabled();
}
