package io.quarkus.search.app.fetching;

import java.time.Duration;
import java.util.OptionalInt;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "fetching")
public interface FetchingConfig {
    OptionalInt parallelism();

    @WithDefault("30s")
    Duration timeout();
}
