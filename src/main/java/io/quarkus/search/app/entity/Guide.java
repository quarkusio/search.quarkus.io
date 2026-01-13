package io.quarkus.search.app.entity;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.quarkus.search.app.hibernate.AnalysisConfigurer;
import io.quarkus.search.app.hibernate.GuideLoadingBinder;
import io.quarkus.search.app.hibernate.I18nFullTextField;
import io.quarkus.search.app.hibernate.I18nKeywordField;
import io.quarkus.search.app.hibernate.InputProvider;
import io.quarkus.search.app.hibernate.InputProviderHtmlBodyTextBridge;

import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Highlightable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.engine.backend.types.TermVector;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.RoutingBinderRef;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.ValueBridgeRef;
import org.hibernate.search.mapper.pojo.loading.mapping.annotation.EntityLoadingBinderRef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.DocumentId;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.SearchEntity;

@SearchEntity(loadingBinder = @EntityLoadingBinderRef(type = GuideLoadingBinder.class))
@Indexed(routingBinder = @RoutingBinderRef(type = QuarkusVersionAndLanguageRoutingBinder.class))
public class Guide {
    @DocumentId
    public URI url;

    @KeywordField(searchable = Searchable.NO, aggregable = Aggregable.YES)
    public Language language;

    @KeywordField(searchable = Searchable.NO, aggregable = Aggregable.YES)
    public String quarkusVersion;

    @KeywordField
    public String type;

    @KeywordField
    public String origin;

    @KeywordField(searchable = Searchable.NO)
    public String status;

    @I18nFullTextField(highlightable = Highlightable.FAST_VECTOR, termVector = TermVector.WITH_POSITIONS_OFFSETS, analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "title_autocomplete", analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nKeywordField(name = "title_sort", normalizerPrefix = AnalysisConfigurer.SORT, searchable = Searchable.NO, sortable = Sortable.YES)
    public I18nData<String> title = new I18nData<>();

    @I18nFullTextField(highlightable = Highlightable.FAST_VECTOR, termVector = TermVector.WITH_POSITIONS_OFFSETS, analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "summary_autocomplete", analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    public I18nData<String> summary = new I18nData<>();

    @I18nFullTextField(analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "keywords_autocomplete", analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    public I18nData<String> keywords = new I18nData<>();

    @I18nFullTextField(name = "fullContent", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), highlightable = Highlightable.FAST_VECTOR, termVector = TermVector.WITH_POSITIONS_OFFSETS, analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "fullContent_autocomplete", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "fullContent_suggestion", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), analyzerPrefix = AnalysisConfigurer.SUGGESTION, searchAnalyzerPrefix = AnalysisConfigurer.SUGGESTION)
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public I18nData<InputProvider> htmlFullContentProvider = new I18nData<>();

    @KeywordField(name = "categories", aggregable = Aggregable.YES)
    public Set<String> categories = Set.of();

    @I18nFullTextField(name = "topics", analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nKeywordField(name = "topics_faceting", searchable = Searchable.YES, projectable = Projectable.YES, aggregable = Aggregable.YES)
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
