package io.quarkus.search.app.dto;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FieldProjection;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.HighlightProjection;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IdProjection;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ProjectionConstructor;

public record GuideSearchHit(String url, String type, String origin, String title, String summary, Set<String> content) {

    @ProjectionConstructor
    public GuideSearchHit(@IdProjection String url,
            @FieldProjection String type,
            @FieldProjection String origin,
            @HighlightProjection List<String> title,
            @HighlightProjection List<String> summary,
            @HighlightProjection(highlighter = "highlighter_content") List<String> fullContent) {
        this(url, type, origin, title.get(0), summary.isEmpty() ? "" : summary.get(0), new LinkedHashSet<>(fullContent));
    }
}
