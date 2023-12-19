package io.quarkus.search.app.indexing;

import java.time.Duration;
import java.util.OptionalInt;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "indexing")
interface IndexingConfig {
    OnStartup onStartup();

    Scheduled scheduled();

    OptionalInt parallelism();

    int batchSize();

    @WithDefault("30s")
    Duration timeout();

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
