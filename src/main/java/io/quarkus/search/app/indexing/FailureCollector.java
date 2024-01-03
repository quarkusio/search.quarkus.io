package io.quarkus.search.app.indexing;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.quarkus.logging.Log;

import org.kohsuke.github.GHIssue;
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
     */
    public record Failure(Level level, Stage stage, String details) {
    }

    private final EnumMap<Level, List<Failure>> failures = new EnumMap<>(Level.class);
    private final Consumer<EnumMap<Level, List<Failure>>> reporter;

    public FailureCollector() throws IOException {
        this.reporter = FailureCollector::logReporter;
    }

    public FailureCollector(IndexingConfig.GitErrorReporting config) throws IOException {
        this(config.type(), config.githubIssue());
    }

    public FailureCollector(IndexingConfig.GitErrorReporting.Type type,
            Optional<IndexingConfig.GitErrorReporting.GithubIssue> githubIssue) throws IOException {
        for (Level value : Level.values()) {
            failures.put(value, new ArrayList<>());
        }
        switch (type) {
            case LOG -> reporter = FailureCollector::logReporter;
            case GITHUB_ISSUE -> {
                IndexingConfig.GitErrorReporting.GithubIssue issue = githubIssue.orElseThrow(
                        () -> new IllegalArgumentException(
                                "GitHub error reporting requires both GitHub repository and issue id to be specified in the properties."));
                reporter = new GithubFailureReporter(issue.repository(), issue.id())::report;
            }
            default -> throw new AssertionError("Unknown reporter type: " + type);
        }
    }

    public void warning(Stage stage, String details) {
        failures.get(Level.WARNING).add(new Failure(Level.WARNING, stage, details));
    }

    public void critical(Stage stage, String details) {
        failures.get(Level.CRITICAL).add(new Failure(Level.CRITICAL, stage, details));
    }

    @Override
    public void close() {
        reporter.accept(failures);
    }

    private static void logReporter(EnumMap<Level, List<Failure>> failures) {
        Log.warn("Reporting indexing status:");
        for (List<Failure> list : failures.values()) {
            for (Failure failure : list) {
                Log.warn(failure);
            }
        }
    }

    private static class GithubFailureReporter {

        private static final String STATUS_CRITICAL = "Critical";
        private static final String STATUS_WARNING = "Warning";
        private static final String STATUS_SUCCESS = "Success";
        private final GitHub github;
        private final String repositoryName;
        private final int issueId;

        GithubFailureReporter(String repositoryName, int issueId) throws IOException {
            this.github = GitHubBuilder.fromEnvironment().build();
            this.repositoryName = repositoryName;
            this.issueId = issueId;

        }

        void report(Map<Level, List<Failure>> failures) {
            Log.trace("Reporting indexing status to GitHub.");
            try {
                GHRepository repository = github.getRepository(repositoryName);
                GHIssue issue = repository.getIssue(issueId);

                String status;
                if (failures.get(Level.CRITICAL).isEmpty()) {
                    if (failures.get(Level.WARNING).isEmpty()) {
                        status = STATUS_SUCCESS;
                        if (GHIssueState.OPEN.equals(issue.getState())) {
                            issue.comment("Indexing finished with no warnings.");
                            issue.close();
                        }
                    } else {
                        status = STATUS_WARNING;
                    }
                } else {
                    status = STATUS_CRITICAL;
                }

                if (!STATUS_SUCCESS.equals(status)) {
                    StringBuilder comment = new StringBuilder("## search.quarkus.io indexing status report: ")
                            .append(status).append('\n');

                    for (Map.Entry<Level, List<Failure>> entry : failures.entrySet()) {
                        report(comment, entry.getValue(), entry.getKey());
                    }

                    issue.comment(comment.toString());

                    if (!GHIssueState.OPEN.equals(issue.getState())) {
                        issue.reopen();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static void report(StringBuilder sb, List<Failure> failures, Level level) {
            if (failures.isEmpty()) {
                return;
            }
            sb.append("\n### ").append(level).append("\n");
            Map<Stage, List<Failure>> map = failures.stream().collect(
                    Collectors.groupingBy(Failure::stage));
            for (Stage stage : Stage.values()) {
                List<Failure> list = map.getOrDefault(stage, List.of());
                if (!list.isEmpty()) {
                    sb.append("* ").append(stage).append(":\n");
                    for (Failure failure : list) {
                        sb.append("  * ").append(failure.details()).append('\n');
                    }
                }
            }
        }
    }
}
