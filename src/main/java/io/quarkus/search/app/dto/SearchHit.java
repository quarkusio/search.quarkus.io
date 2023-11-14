package io.quarkus.search.app.dto;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.HighlightProjection;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IdProjection;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ProjectionConstructor;

public record SearchHit(String id, String title, String summary, Set<String> content) {

    @ProjectionConstructor
    public SearchHit(@IdProjection String id,
            @HighlightProjection List<String> title,
            @HighlightProjection List<String> summary,
            @HighlightProjection(highlighter = "highlighter_content") List<String> fullContent) {
        this(id, title.get(0), summary.isEmpty() ? "" : summary.get(0), new LinkedHashSet<>(fullContent));
    }
}
