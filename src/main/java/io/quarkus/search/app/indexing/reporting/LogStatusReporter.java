package io.quarkus.search.app.indexing.reporting;

import static io.quarkus.search.app.indexing.reporting.StatusRenderer.toStatusDetailsMarkdown;

import java.util.List;
import java.util.Map;

import io.quarkus.logging.Log;

public class LogStatusReporter implements StatusReporter {
    @Override
    public void report(Status status, Map<FailureCollector.Level, List<Failure>> failures) {
        // failures are an enum map that we pre-initialize, hence we check if there's anything in the lists:
        if (failures.isEmpty() || failures.values().stream().allMatch(List::isEmpty)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        toStatusDetailsMarkdown(sb, failures, false);
        Log.warn(sb);
    }
}
