package io.quarkus.search.app.entity;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import io.quarkus.search.app.hibernate.AnalysisConfigurer;
import io.quarkus.search.app.hibernate.I18nFullTextField;
import io.quarkus.search.app.hibernate.I18nKeywordField;
import io.quarkus.search.app.hibernate.InputProvider;
import io.quarkus.search.app.hibernate.InputProviderHtmlBodyTextBridge;
import io.quarkus.search.app.hibernate.URIType;

import org.hibernate.annotations.JavaType;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Highlightable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.engine.backend.types.TermVector;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.RoutingBinderRef;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.ValueBridgeRef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

@Entity
@Indexed(routingBinder = @RoutingBinderRef(type = QuarkusVersionAndLanguageRoutingBinder.class))
public class Guide {
    @Id
    @JavaType(URIType.class)
    public URI url;

    @Enumerated(EnumType.STRING)
    @KeywordField(searchable = Searchable.NO, aggregable = Aggregable.YES)
    public Language language;

    @KeywordField(searchable = Searchable.NO, aggregable = Aggregable.YES)
    public String quarkusVersion;

    @KeywordField
    public String type;

    @KeywordField
    public String origin;

    @I18nFullTextField(highlightable = Highlightable.UNIFIED, termVector = TermVector.WITH_POSITIONS_OFFSETS, analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "title_autocomplete", analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nKeywordField(name = "title_sort", normalizerPrefix = AnalysisConfigurer.SORT, searchable = Searchable.NO, sortable = Sortable.YES)
    @Embedded
    public I18nData<String> title = new I18nData<>();

    @I18nFullTextField(highlightable = Highlightable.UNIFIED, termVector = TermVector.WITH_POSITIONS_OFFSETS, analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "summary_autocomplete", analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @Embedded
    public I18nData<String> summary = new I18nData<>();

    @I18nFullTextField(analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "keywords_autocomplete", analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @Embedded
    public I18nData<String> keywords = new I18nData<>();

    @I18nFullTextField(name = "fullContent", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), highlightable = Highlightable.UNIFIED, termVector = TermVector.WITH_POSITIONS_OFFSETS, analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "fullContent_autocomplete", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @Transient
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public I18nData<InputProvider> htmlFullContentProvider = new I18nData<>();

    @KeywordField(name = "categories", aggregable = Aggregable.YES)
    public Set<String> categories = Set.of();

    @I18nFullTextField(name = "topics", analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nKeywordField(name = "topics_faceting", searchable = Searchable.YES, projectable = Projectable.YES, aggregable = Aggregable.YES)
    @ElementCollection
    public List<I18nData<String>> topics = List.of();

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
        return getClass().getSimpleName() + "{" +
                "url=<" + url + '>' +
                '}';
    }
}
