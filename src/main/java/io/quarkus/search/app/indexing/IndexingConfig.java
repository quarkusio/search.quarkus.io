package io.quarkus.search.app.indexing;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "indexing")
public interface IndexingConfig {
    OnStartup onStartup();

    Scheduled scheduled();

    OptionalInt parallelism();

    int batchSize();

    @WithDefault("30s")
    Duration timeout();

    GitErrorReporting errorReporting();

    interface OnStartup {
        @WithDefault("true")
        boolean enabled();

        @WithDefault("3s")
        Duration waitInterval();
    }

    interface Scheduled {
        String cron();
    }

    interface GitErrorReporting {
        @WithDefault("log")
        Type type();

        Optional<GithubIssue> githubIssue();

        interface GithubIssue {
            String repository();

            int id();
        }

        enum Type {
            LOG,
            GITHUB_ISSUE;
        }
    }
}
