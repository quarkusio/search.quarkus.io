package io.quarkus.search.app.dto;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record GuideSearchHit(URI url, String type, String origin, String title, String summary, Set<String> content) {

    public GuideSearchHit(URI url,
            String type,
            String origin,
            Optional<String> title,
            Optional<String> summary,
            List<String> content) {
        this(url, type, origin, title.orElse(""), summary.orElse(""), wrap(content));
    }

    @SuppressWarnings("unchecked")
    public GuideSearchHit(List<?> values) {
        this(
                (URI) values.get(0), (String) values.get(1), (String) values.get(2),
                (Optional<String>) values.get(3), (Optional<String>) values.get(4),
                (List<String>) values.get(5));
    }

    private static Set<String> wrap(List<String> strings) {
        Set<String> set = new LinkedHashSet<>();
        for (String string : strings) {
            set.add("…%s…".formatted(string));
        }
        return set;
    }

}
