package io.quarkus.search.app.indexing.reporting;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.config.WithDefault;

public interface ReportingConfig {
    @WithDefault("log")
    Type type();

    Optional<GithubReporter> github();

    interface GithubReporter {
        GithubReporter.Issue issue();

        String token();

        /**
         * @return How often to report status on GitHub when the last report was identical and contained only warnings.
         */
        Duration warningRepeatDelay();

        /**
         * @return The comment count threshold that triggers packing (deleting old comments).
         *         GitHub limits issues to 2500 comments, so this should be set below that.
         */
        int commentPackThreshold();

        /**
         * @return The number of most recent comments to retain when packing.
         */
        int commentPackRetained();

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
