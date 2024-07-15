package io.quarkus.search.app.indexing.reporting;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class StatusRenderer {
    private static final String TITLE_UPDATED_AND_STATUS_FORMAT = ": %s (updated %s)";
    private static final Pattern TITLE_UPDATED_AND_STATUS_PATTERN = Pattern
            .compile("(:\s*([^() ]+) )?\s*\\(updated [^)]+\\)");
    private static final DateTimeFormatter UPDATED_DATE_FORMAT = DateTimeFormatter.ofPattern(
            "uuuu-MM-dd'T'HH:mm:ssZZZZZ",
            Locale.ROOT);

    private static final int GITHUB_MAX_COMMENT_LENGTH = 65536;
    private static final String INSERT_START_MARKER = "<!-- Automatic message start -->";
    private static final String INSERT_END_MARKER = "<!-- Automatic message end -->";

    public static String toStatusSummary(Clock clock, Status status, String previousSummary) {
        String toInsert = TITLE_UPDATED_AND_STATUS_FORMAT.formatted(
                formatStatus(status),
                UPDATED_DATE_FORMAT.format(clock.instant().atOffset(ZoneOffset.UTC)));
        String result = TITLE_UPDATED_AND_STATUS_PATTERN.matcher(previousSummary).replaceAll(toInsert);
        if (result.equals(previousSummary)) {
            // The previous summary didn't contain any mention of the status and last update; add it.
            result = result + toInsert;
        }
        return result;
    }

    private static Object formatStatus(Status status) {
        return switch (status) {
            case SUCCESS -> "Success";
            case WARNING -> "Warning";
            case CRITICAL -> "Critical";
            case UNSTABLE -> "Unstable";
        };
    }

    public static void toStatusDetailsMarkdown(StringBuilder sb, Map<FailureCollector.Level, List<Failure>> failures,
            boolean includeException) {
        for (Map.Entry<FailureCollector.Level, List<Failure>> entry : failures.entrySet()) {
            toStatusDetailsMarkdown(sb, entry.getValue(), entry.getKey(), includeException);
        }
    }

    private static void toStatusDetailsMarkdown(StringBuilder sb, List<Failure> failures, FailureCollector.Level level,
            boolean includeException) {
        if (failures.isEmpty()) {
            return;
        }
        sb.append("\n### ").append(level).append("\n");
        Map<FailureCollector.Stage, Set<Failure>> map = failures.stream().collect(
                // Sort failures so that two runs with the same failures will produce the same report
                // This is critical when we try to limit the frequency of identical reports (see GH reporter)
                Collectors.groupingBy(Failure::stage, Collectors.toCollection(() -> new TreeSet<>(Failure.COMPARATOR))));
        for (FailureCollector.Stage stage : FailureCollector.Stage.values()) {
            Set<Failure> set = map.getOrDefault(stage, Set.of());
            if (!set.isEmpty()) {
                sb.append("<details>\n");
                sb.append("  <summary>Issues with <code>").append(stage).append("</code>:</summary>\n\n");
                for (Failure failure : set) {
                    sb.append("  * ").append(failure.details()).append('\n');
                    if (includeException) {
                        formatException(sb, failure.exception());
                    }
                }
                sb.append("</details>\n");
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
                .append("</code></summary>\n\n    ```java\n");

        try (StringWriter writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer)) {
            exception.printStackTrace(printWriter);
            sb.append(writer.toString().replaceAll("(?m)^", "    "));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        sb.append("\n    ```\n    </details>\n\n");
    }

    static String insertMessageInIssueDescription(String originalIssueDescription, String newMessage) {
        StringBuilder result = new StringBuilder(originalIssueDescription != null ? originalIssueDescription : "");
        int startMarkerIndex = result.indexOf(INSERT_START_MARKER);
        int endMarkerIndex = startMarkerIndex < 0 ? -1 : result.indexOf(INSERT_END_MARKER);
        if (startMarkerIndex >= 0 && endMarkerIndex >= 0) {
            result.replace(startMarkerIndex + INSERT_START_MARKER.length(), endMarkerIndex, "\n");
        } else {
            result.append('\n');
            startMarkerIndex = result.length();
            result.append(INSERT_START_MARKER).append('\n').append(INSERT_END_MARKER);
        }
        int currentIndex = startMarkerIndex + INSERT_START_MARKER.length();

        String quoteIntroMessage = "\n## Last update\n";
        result.insert(currentIndex, quoteIntroMessage);
        currentIndex += quoteIntroMessage.length();

        String truncatedMessage = truncateForGitHubMaxLength(asMarkdownQuote(newMessage), result.length());
        result.insert(currentIndex, truncatedMessage);

        return result.toString();
    }

    static String truncateForGitHubMaxLength(String message, int reservedLength) {
        int maxLength = GITHUB_MAX_COMMENT_LENGTH - reservedLength;
        if (message.length() > maxLength) {
            return ("### Message truncated as it was too long\n" + message)
                    .substring(0, maxLength);
        } else {
            return message;
        }
    }

    private static String asMarkdownQuote(String string) {
        return string.lines().map(s -> "> " + s).collect(Collectors.joining("\n"));
    }
}
