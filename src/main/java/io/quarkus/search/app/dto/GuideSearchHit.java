package io.quarkus.search.app.dto;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record GuideSearchHit(URI url, String type, String status, String origin, String title, String summary,
        Set<String> categories, Set<String> subcategories) {

    public GuideSearchHit(URI url,
            String type,
            String status,
            String origin,
            Optional<String> title,
            Optional<String> summary,
            List<String> categories,
            List<String> subcategories) {
        this(url, type, status, origin, title.orElse(""), summary.orElse(""),
                categories == null ? Set.of() : new LinkedHashSet<>(categories),
                subcategories == null ? Set.of() : new LinkedHashSet<>(subcategories));
    }

    @SuppressWarnings("unchecked")
    public GuideSearchHit(List<?> values) {
        this(
                (URI) values.get(0), (String) values.get(1), (String) values.get(2), (String) values.get(3),
                (Optional<String>) values.get(4), (Optional<String>) values.get(5),
                (List<String>) values.get(6),
                (List<String>) values.get(7));
    }

}
