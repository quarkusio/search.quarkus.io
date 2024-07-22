package io.quarkus.search.app.indexing.state;

import java.time.Duration;

import io.smallrye.config.WithDefault;

public interface RetryConfig {
    @WithDefault("3")
    int maxAttempts();

    @WithDefault("1M") // 1 Minute
    Duration delay();
}
