package io.quarkus.search.app.indexing.reporting;

import static io.quarkus.search.app.indexing.reporting.StatusRenderer.toStatusDetailsMarkdown;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import io.quarkus.logging.Log;

public class LogStatusReporter implements StatusReporter {

    private final Clock clock;

    public LogStatusReporter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void report(Status status, Map<FailureCollector.Level, List<Failure>> failures) {
        StringBuilder sb = new StringBuilder(StatusRenderer.toStatusSummary(clock, status, "Indexing status"));
        switch (status) {
            case SUCCESS -> {
                Log.info(sb);
            }
            case WARNING, UNSTABLE -> {
                toStatusDetailsMarkdown(sb, failures, false);
                Log.warn(sb);
            }
            case CRITICAL -> {
                toStatusDetailsMarkdown(sb, failures, false);
                Log.error(sb);
            }
        }
    }
}
