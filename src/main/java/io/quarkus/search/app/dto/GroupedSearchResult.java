package io.quarkus.search.app.dto;

import java.util.List;

public record GroupedSearchResult(List<CategoryGroup> categories, SearchResult.Suggestion suggestion) {

    public GroupedSearchResult(List<CategoryGroup> categories) {
        this(categories, null);
    }

    public record CategoryGroup(String category, long hitCount, List<GroupedGuideHit> hits) {
    }

}
