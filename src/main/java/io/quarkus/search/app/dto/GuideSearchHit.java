package io.quarkus.search.app.dto;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record GuideSearchHit(URI url, String type, String origin, String title, String summary, Set<String> content) {

    public GuideSearchHit(URI url,
            String type,
            String origin,
            List<String> title,
            List<String> summary,
            List<String> fullContent) {
        this(url, type, origin, firstOrEmpty(title), firstOrEmpty(summary), new LinkedHashSet<>(fullContent));
    }

    @SuppressWarnings("unchecked")
    public GuideSearchHit(List<?> values) {
        this(
                (URI) values.get(0), (String) values.get(1), (String) values.get(2),
                (List<String>) values.get(3), (List<String>) values.get(4), (List<String>) values.get(5));
    }

    private static String firstOrEmpty(List<String> strings) {
        return strings.isEmpty() ? "" : strings.get(0);
    }
}
