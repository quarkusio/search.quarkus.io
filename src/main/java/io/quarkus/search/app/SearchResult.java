package io.quarkus.search.app;

import java.util.List;

public record SearchResult<T> (long total, List<T> hits) {
}
