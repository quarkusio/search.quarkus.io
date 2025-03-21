package io.quarkus.search.app.dto;

import java.util.List;

import org.hibernate.search.backend.elasticsearch.search.query.ElasticsearchSearchResult;

public record SearchResult<T>(Total total, List<T> hits, Suggestion suggestion) {

    public SearchResult(ElasticsearchSearchResult<T> result) {
        this(new Total(result.total().isHitCountExact() ? result.total().hitCount() : null,
                result.total().hitCountLowerBound()),
                result.hits(), null);
    }

    public SearchResult(ElasticsearchSearchResult<T> result, Suggestion suggestion) {
        this(new Total(result.total().isHitCountExact() ? result.total().hitCount() : null,
                result.total().hitCountLowerBound()),
                result.hits(), suggestion);
    }

    public record Total(Long exact, Long lowerBound) {
    }

    public record Suggestion(String query, String highlighted) {
    }

}
