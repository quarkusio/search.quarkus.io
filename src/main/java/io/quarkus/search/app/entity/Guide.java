package io.quarkus.search.app.entity;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import io.quarkus.search.app.hibernate.AnalysisConfigurer;
import io.quarkus.search.app.hibernate.InputProvider;
import io.quarkus.search.app.hibernate.InputProviderHtmlBodyTextBridge;
import io.quarkus.search.app.hibernate.URIType;

import org.hibernate.Length;
import org.hibernate.annotations.JavaType;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Highlightable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.engine.backend.types.TermVector;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.ValueBridgeRef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

@Entity
@Indexed
public class Guide {
    @Id
    @JavaType(URIType.class)
    public URI url;

    @KeywordField
    public String version;

    @KeywordField
    public String type;

    @KeywordField
    public String origin;

    @FullTextField(highlightable = Highlightable.UNIFIED, termVector = TermVector.WITH_POSITIONS_OFFSETS, analyzer = AnalysisConfigurer.DEFAULT, searchAnalyzer = AnalysisConfigurer.DEFAULT_SEARCH)
    @FullTextField(name = "title_autocomplete", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT_SEARCH)
    @KeywordField(name = "title_sort", normalizer = AnalysisConfigurer.SORT, searchable = Searchable.NO, sortable = Sortable.YES)
    @Column(length = Length.LONG)
    public String title;

    @FullTextField(highlightable = Highlightable.UNIFIED, termVector = TermVector.WITH_POSITIONS_OFFSETS, analyzer = AnalysisConfigurer.DEFAULT, searchAnalyzer = AnalysisConfigurer.DEFAULT_SEARCH)
    @FullTextField(name = "summary_autocomplete", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT_SEARCH)
    @Column(length = Length.LONG32)
    public String summary;

    @FullTextField(analyzer = AnalysisConfigurer.DEFAULT, searchAnalyzer = AnalysisConfigurer.DEFAULT_SEARCH)
    @FullTextField(name = "keywords_autocomplete", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT_SEARCH)
    @Column(length = Length.LONG32)
    public String keywords;

    @FullTextField(name = "fullContent", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), highlightable = Highlightable.UNIFIED, analyzer = AnalysisConfigurer.DEFAULT, searchAnalyzer = AnalysisConfigurer.DEFAULT_SEARCH)
    @FullTextField(name = "fullContent_autocomplete", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT_SEARCH)
    @Transient
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public InputProvider htmlFullContentProvider;

    @KeywordField(name = "categories")
    public Set<String> categories = Set.of();

    @FullTextField(name = "topics", analyzer = AnalysisConfigurer.DEFAULT, searchAnalyzer = AnalysisConfigurer.DEFAULT_SEARCH)
    @KeywordField(name = "topics_faceting", searchable = Searchable.YES, projectable = Projectable.YES, aggregable = Aggregable.YES)
    public Set<String> topics = Set.of();

    @KeywordField(name = "extensions_faceting", searchable = Searchable.YES, projectable = Projectable.YES, aggregable = Aggregable.YES)
    public Set<String> extensions = Set.of();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Guide guide = (Guide) o;
        return Objects.equals(url, guide.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return "Guide{" +
                "url='" + url + '\'' +
                '}';
    }
}
