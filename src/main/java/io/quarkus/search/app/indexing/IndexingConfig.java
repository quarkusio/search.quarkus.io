package io.quarkus.search.app.indexing;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "indexing")
interface IndexingConfig {
    OnStartup onStartup();

    Scheduled scheduled();

    interface OnStartup {
        @WithDefault("true")
        boolean enabled();

        @WithDefault("3s")
        Duration waitInterval();
    }

    interface Scheduled {
        String cron();
    }
}
