package io.quarkus.search.app.indexing;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "indexing")
interface IndexingConfig {
    OnStartup onStartup();

    interface OnStartup {
        @WithDefault("true")
        boolean enabled();

        @WithDefault("3s")
        Duration waitInterval();
    }
}
