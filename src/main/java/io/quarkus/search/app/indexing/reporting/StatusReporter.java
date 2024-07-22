package io.quarkus.search.app.indexing.reporting;

import java.time.Clock;
import java.util.List;
import java.util.Map;

public interface StatusReporter {

    void report(Status status, Map<FailureCollector.Level, List<Failure>> failures);

    static StatusReporter create(ReportingConfig reportingConfig, Clock clock) {
        var type = reportingConfig.type();
        return switch (type) {
            case LOG -> new LogStatusReporter(clock);
            case GITHUB_ISSUE -> {
                ReportingConfig.GithubReporter github = reportingConfig.github().orElseThrow(
                        () -> new IllegalArgumentException(
                                "GitHub error reporting requires both GitHub repository and issue id to be specified in the properties."));
                yield new GithubStatusReporter(clock, github);
            }
        };
    }
}
