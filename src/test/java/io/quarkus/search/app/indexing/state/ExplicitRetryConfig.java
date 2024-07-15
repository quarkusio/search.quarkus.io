package io.quarkus.search.app.indexing.state;

import java.time.Duration;

public record ExplicitRetryConfig(
        int maxAttempts,
        Duration delay)
        implements
            RetryConfig {
}
