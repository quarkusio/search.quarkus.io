package io.quarkus.search.app.indexing.reporting;

import static io.quarkus.search.app.indexing.reporting.StatusRenderer.toStatusDetailsMarkdown;
import static io.quarkus.search.app.indexing.reporting.StatusRenderer.toStatusSummary;
import static io.quarkus.search.app.util.Streams.toStream;
import static io.quarkus.search.app.util.UncheckedIOFunction.uncheckedIO;

import java.io.IOException;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.quarkus.search.app.util.Streams;

import io.quarkus.logging.Log;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

class GithubStatusReporter implements StatusReporter {

    private static final String STATUS_REPORT_HEADER = "## search.quarkus.io indexing status: ";

    private final Clock clock;
    private final ReportingConfig.GithubReporter config;

    GithubStatusReporter(Clock clock, ReportingConfig.GithubReporter config) {
        this.clock = clock;
        this.config = config;
    }

    @Override
    public void report(Status status, Map<FailureCollector.Level, List<Failure>> failures) {
        Log.infof("Reporting indexing status to GitHub.");
        try {
            GitHub github = new GitHubBuilder().withOAuthToken(config.token()).build();
            GHRepository repository = github.getRepository(config.issue().repository());
            GHIssue issue = repository.getIssue(config.issue().id());

            // Update last indexing date:
            issue.setTitle(toStatusSummary(clock, status, issue.getTitle()));

            // Build a status report
            StringBuilder newReportBuilder = new StringBuilder(STATUS_REPORT_HEADER)
                    .append(status).append('\n');
            toStatusDetailsMarkdown(newReportBuilder, failures, true);
            String newReport = newReportBuilder.toString();

            if (status == Status.IN_PROGRESS) {
                // Don't update the issue description before completion.
            } else {
                // On completion, update the issue description with the content of the latest comment,
                // for convenience, and to have information available even when indexing is unstable (no issue comment).
                // This must be done before the comment, so that notifications triggered by the comment are only sent
                // when the issue is fully updated.
                issue.setBody(StatusRenderer.insertMessageInIssueDescription(issue.getBody(), newReport));
            }

            // add comments if needed:
            String reportComment = StatusRenderer.truncateForGitHubMaxLength(newReport, 0);
            switch (status) {
                case IN_PROGRESS, SUCCESS -> {
                    // When in progress or successful, never comment: we want to avoid unnecessary noise.
                }
                case WARNING -> {
                    // When warning, only comment if we didn't comment the same thing recently.
                    var lastRecentCommentByMe = getStatusCommentsSince(
                            issue,
                            clock.instant().minus(config.warningRepeatDelay()))
                            .reduce(Streams.last());
                    if (lastRecentCommentByMe.isPresent()
                            && lastRecentCommentByMe.get().getBody().contentEquals(reportComment)) {
                        Log.infof("Skipping the issue comment because the same message was sent recently.");
                    } else {
                        issue.comment(reportComment);
                    }
                }
                case UNSTABLE -> {
                    // When unstable, never comment: there'll be a retry, and we want to avoid unnecessary noise.
                }
                case CRITICAL ->
                    // When critical, always comment.
                    issue.comment(reportComment);
            }

            // handle issue state (open/close):
            switch (status) {
                case IN_PROGRESS -> {
                    Log.infof("Leaving GitHub issue in its current open/close state pending completion.");
                }
                case SUCCESS, WARNING -> {
                    if (GHIssueState.OPEN.equals(issue.getState())) {
                        Log.infof("Closing GitHub issue as indexing succeeded.");
                        issue.close();
                    }
                }
                case UNSTABLE -> {
                    Log.infof("Leaving GitHub issue in its current open/close state pending retry.");
                }
                case CRITICAL -> {
                    if (GHIssueState.CLOSED.equals(issue.getState())) {
                        Log.infof("Opening GitHub issue due to critical failures.");
                        issue.reopen();
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Unable to report failures to GitHub: " + e.getMessage(), e);
        }
    }

    private Stream<GHIssueComment> getStatusCommentsSince(GHIssue issue, Instant since) {
        return toStream(issue.queryComments().since(Date.from(since)).list())
                .filter(uncheckedIO(
                        (GHIssueComment comment) -> comment.getBody().startsWith(STATUS_REPORT_HEADER))::apply);
    }
}
