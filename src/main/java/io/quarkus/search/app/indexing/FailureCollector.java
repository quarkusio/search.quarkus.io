package io.quarkus.search.app.indexing;

import static io.quarkus.search.app.util.Streams.toStream;
import static io.quarkus.search.app.util.UncheckedIOFunction.uncheckedIO;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Date;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.search.app.util.Streams;

import io.quarkus.logging.Log;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public class FailureCollector implements Closeable {

    public enum Level {
        CRITICAL,
        WARNING;
    }

    public enum Stage {
        PARSING,
        TRANSLATION,
        INDEXING;
    }

    /**
     * @param level Whether the reported failure should lead to reporting the indexing as incomplete/failed or not.
     * @param stage Where the failure happened.
     * @param details Failure details.
     * @param exception An exception that has caused the failure.
     */
    public record Failure(Level level, Stage stage, String details, Exception exception) {
        static Comparator<Failure> COMPARATOR = Comparator.comparing(Failure::level)
                .thenComparing(Failure::stage)
                .thenComparing(Failure::details)
                // Not perfect, but then how likely it is to get
                // two failures with everything identical except the exception?
                .thenComparing(f -> System.identityHashCode(f.exception()));
    }

    private final EnumMap<Level, List<Failure>> failures = new EnumMap<>(Level.class);
    private final Consumer<EnumMap<Level, List<Failure>>> reporter;

    public FailureCollector() {
        this.reporter = FailureCollector::logReporter;
    }

    public FailureCollector(IndexingConfig.GitErrorReporting config) {
        this(config.type(), config.github());
    }

    public FailureCollector(IndexingConfig.GitErrorReporting.Type type,
            Optional<IndexingConfig.GitErrorReporting.GithubReporter> githubOptional) {
        for (Level value : Level.values()) {
            failures.put(value, Collections.synchronizedList(new ArrayList<>()));
        }
        switch (type) {
            case LOG -> reporter = FailureCollector::logReporter;
            case GITHUB_ISSUE -> {
                IndexingConfig.GitErrorReporting.GithubReporter github = githubOptional.orElseThrow(
                        () -> new IllegalArgumentException(
                                "GitHub error reporting requires both GitHub repository and issue id to be specified in the properties."));
                reporter = new GithubFailureReporter(github)::report;
            }
            default -> throw new AssertionError("Unknown reporter type: " + type);
        }
    }

    public void warning(Stage stage, String details) {
        warning(stage, details, null);
    }

    public void warning(Stage stage, String details, Exception exception) {
        Log.warn(details, exception);
        failures.get(Level.WARNING).add(new Failure(Level.WARNING, stage, details, exception));
    }

    public void critical(Stage stage, String details, Exception exception) {
        Log.error(details, exception);
        failures.get(Level.CRITICAL).add(new Failure(Level.CRITICAL, stage, details, exception));
    }

    @Override
    public void close() {
        reporter.accept(failures);
    }

    private static void logReporter(EnumMap<Level, List<Failure>> failures) {
        if (failures.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        toMarkdown(sb, failures, false);
        Log.warn(sb);
    }

    private static class GithubFailureReporter {

        private static final String STATUS_CRITICAL = "Critical";
        private static final String STATUS_WARNING = "Warning";
        private static final String STATUS_SUCCESS = "Success";
        private static final String STATUS_REPORT_HEADER = "## search.quarkus.io indexing status: ";
        private static final String UPDATED_FORMAT = "(updated %s)";
        private static final Pattern UPDATED_PATTERN = Pattern.compile("\\(updated [^)]+\\)");
        private static final DateTimeFormatter UPDATED_DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssZZZZZ",
                Locale.ROOT);
        private final IndexingConfig.GitErrorReporting.GithubReporter config;

        GithubFailureReporter(IndexingConfig.GitErrorReporting.GithubReporter config) {
            this.config = config;
        }

        void report(Map<Level, List<Failure>> failures) {
            Log.infof("Reporting indexing status to GitHub.");
            try {
                GitHub github = new GitHubBuilder().withOAuthToken(config.token()).build();
                GHRepository repository = github.getRepository(config.issue().repository());
                GHIssue issue = repository.getIssue(config.issue().id());

                String status = indexingResultStatus(failures);

                // add comments if needed:
                if (!STATUS_SUCCESS.equals(status)) {
                    StringBuilder newMessage = new StringBuilder(STATUS_REPORT_HEADER)
                            .append(status).append('\n');

                    toMarkdown(newMessage, failures, true);

                    if (STATUS_WARNING.equals(status)) {
                        var lastRecentCommentByMe = getStatusCommentsSince(issue,
                                Instant.now().minus(config.warningRepeatDelay()))
                                .reduce(Streams.last());
                        // For warnings, only comment if we didn't comment the same thing recently.
                        if (lastRecentCommentByMe.isPresent()
                                && lastRecentCommentByMe.get().getBody().contentEquals(newMessage)) {
                            Log.infof("Skipping the issue comment because the same message was sent recently.");
                        } else {
                            issue.comment(newMessage.toString());
                        }
                    } else {
                        // For errors, always comment.
                        issue.comment(newMessage.toString());
                    }
                }

                // Update last indexing date:
                issue.setTitle(insertUpdateDate(issue.getTitle()));

                // handle issue state (open/close):
                //   Only reopen/keep opened an issue if we have critical things to report.
                //   Otherwise, let's limit it to a comment only, and close an issue if needed.
                if (STATUS_CRITICAL.equals(status) && !GHIssueState.OPEN.equals(issue.getState())) {
                    Log.infof("Opening GitHub issue due to critical errors.");
                    issue.reopen();
                }
                if (!STATUS_CRITICAL.equals(status) && GHIssueState.OPEN.equals(issue.getState())) {
                    Log.infof("Closing GitHub issue as indexing succeeded.");
                    issue.close();
                }
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("Unable to report failures to GitHub: " + e.getMessage(), e);
            }
        }

        private String insertUpdateDate(String title) {
            String toInsert = UPDATED_FORMAT.formatted(UPDATED_DATE_FORMAT.format(Instant.now().atOffset(ZoneOffset.UTC)));
            String result = UPDATED_PATTERN.matcher(title).replaceAll(toInsert);
            if (result.equals(title)) {
                // The title didn't contain any mention of the last update; add it.
                result = result + " " + toInsert;
            }
            return result;
        }

        private Stream<GHIssueComment> getStatusCommentsSince(GHIssue issue, Instant since) {
            return toStream(issue.queryComments().since(Date.from(since)).list())
                    .filter(uncheckedIO((GHIssueComment comment) -> comment.getBody().startsWith(STATUS_REPORT_HEADER))::apply);
        }

        private static String indexingResultStatus(Map<Level, List<Failure>> failures) {
            if (failures.get(Level.CRITICAL).isEmpty()) {
                if (failures.get(Level.WARNING).isEmpty()) {
                    return STATUS_SUCCESS;
                } else {
                    return STATUS_WARNING;
                }
            } else {
                return STATUS_CRITICAL;
            }
        }
    }

    private static void toMarkdown(StringBuilder sb, Map<Level, List<Failure>> failures, boolean includeException) {
        for (Map.Entry<Level, List<Failure>> entry : failures.entrySet()) {
            toMarkdown(sb, entry.getValue(), entry.getKey(), includeException);
        }
    }

    private static void toMarkdown(StringBuilder sb, List<Failure> failures, Level level, boolean includeException) {
        if (failures.isEmpty()) {
            return;
        }
        sb.append("\n### ").append(level).append("\n");
        Map<Stage, Set<Failure>> map = failures.stream().collect(
                // Sort failures so that two runs with the same failures will produce the same report
                // This is critical when we try to limit the frequency of identical reports (see GH reporter)
                Collectors.groupingBy(Failure::stage, Collectors.toCollection(() -> new TreeSet<>(Failure.COMPARATOR))));
        for (Stage stage : Stage.values()) {
            Set<Failure> set = map.getOrDefault(stage, Set.of());
            if (!set.isEmpty()) {
                sb.append("* ").append(stage).append(":\n");
                for (Failure failure : set) {
                    sb.append("  * ").append(failure.details()).append('\n');
                    if (includeException) {
                        formatException(sb, failure.exception());
                    }
                }
            }
        }
    }

    private static void formatException(StringBuilder sb, Exception exception) {
        if (exception == null) {
            return;
        }

        sb.append("\n    <details>\n")
                .append("      <summary>")
                .append("Exception details: <code>").append(exception.getClass().getName())
                .append("</code></summary>\n\n");

        try (StringWriter writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer)) {
            exception.printStackTrace(printWriter);
            sb.append(writer.toString().replaceAll("(?m)^", "        "));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        sb.append("\n    </details>\n\n");
    }
}
