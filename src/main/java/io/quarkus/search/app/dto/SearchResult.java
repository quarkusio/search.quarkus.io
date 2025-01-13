package io.quarkus.search.app.dto;

import java.util.List;

import io.quarkus.logging.Log;

import org.hibernate.search.backend.elasticsearch.search.query.ElasticsearchSearchResult;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public record SearchResult<T>(Total total, List<T> hits, Suggestion suggestion) {

    public SearchResult(ElasticsearchSearchResult<T> result) {
        this(new Total(result.total().isHitCountExact() ? result.total().hitCount() : null,
                result.total().hitCountLowerBound()),
                result.hits(), extractSuggestion(result));
    }

    public record Total(Long exact, Long lowerBound) {
    }

    public record Suggestion(String query, String highlighted) {
    }

    private static Suggestion extractSuggestion(ElasticsearchSearchResult<?> result) {
        try {
            JsonObject suggest = result.responseBody().getAsJsonObject("suggest");
            if (suggest != null) {
                JsonArray options = suggest
                        .getAsJsonArray("didYouMean")
                        .get(0).getAsJsonObject()
                        .getAsJsonArray("options");
                if (options != null && !options.isEmpty()) {
                    JsonObject suggestion = options.get(0).getAsJsonObject();
                    return new Suggestion(suggestion.get("text").getAsString(), suggestion.get("highlighted").getAsString());
                }
            }
        } catch (RuntimeException e) {
            // Though it shouldn't happen, just in case we will catch any exceptions and return no suggestions:
            Log.warnf(e, "Failed to extract suggestion: %s" + e.getMessage());
        }
        return null;
    }
}
