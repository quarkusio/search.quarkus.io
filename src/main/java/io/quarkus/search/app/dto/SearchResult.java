package io.quarkus.search.app.dto;

import java.util.List;

public record SearchResult<T>(Total total, List<T> hits) {
    public SearchResult(org.hibernate.search.engine.search.query.SearchResult<T> result) {
        this(new Total(result.total().isHitCountExact() ? result.total().hitCount() : null,
                result.total().hitCountLowerBound()),
                result.hits());
    }

    public record Total(Long exact, Long lowerBound) {
    }
}
