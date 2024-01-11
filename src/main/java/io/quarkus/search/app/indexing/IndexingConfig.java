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
        @WithDefault("always")
        When when();

        @WithDefault("3s")
        Duration waitInterval();

        enum When {
            ALWAYS,
            INDEXES_EMPTY,
            NEVER
        }
    }

    interface Scheduled {
        String cron();
    }

    interface GitErrorReporting {
        @WithDefault("log")
        Type type();

        Optional<GithubReporter> github();

        interface GithubReporter {
            Issue issue();

            String token();

            /**
             * @return How often to report status on GitHub when the last report was identical and contained only warnings.
             */
            Duration warningRepeatDelay();

            interface Issue {
                String repository();

                int id();
            }
        }

        enum Type {
            LOG,
            GITHUB_ISSUE;
        }
    }
}
