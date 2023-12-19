package io.quarkus.search.app.entity;

import static io.quarkus.search.app.quarkusio.QuarkusIO.QUARKUS_ORIGIN;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import io.quarkus.search.app.hibernate.AnalysisConfigurer;
import io.quarkus.search.app.hibernate.I18nFullTextField;
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
import org.hibernate.search.mapper.pojo.bridge.builtin.annotation.AlternativeDiscriminator;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.RoutingBinderRef;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.ValueBridgeRef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

@Entity
@Indexed(routingBinder = @RoutingBinderRef(type = VersionAndLanguageRoutingBinder.class))
public class Guide {
    @Id
    @JavaType(URIType.class)
    public URI url;

    @AlternativeDiscriminator
    @Enumerated(EnumType.STRING)
    public Language language;

    public String version;

    @KeywordField
    public String type;

    @KeywordField
    public String origin;

    @I18nFullTextField(highlightable = Highlightable.UNIFIED, termVector = TermVector.WITH_POSITIONS_OFFSETS, analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "title_autocomplete", analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @KeywordField(name = "title_sort", normalizer = AnalysisConfigurer.SORT, searchable = Searchable.NO, sortable = Sortable.YES)
    @Column(length = Length.LONG)
    public String title;

    @I18nFullTextField(highlightable = Highlightable.UNIFIED, termVector = TermVector.WITH_POSITIONS_OFFSETS, analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "summary_autocomplete", analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @Column(length = Length.LONG32)
    public String summary;

    @I18nFullTextField(analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "keywords_autocomplete", analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @Column(length = Length.LONG32)
    public String keywords;

    @I18nFullTextField(name = "fullContent", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), highlightable = Highlightable.UNIFIED, analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @I18nFullTextField(name = "fullContent_autocomplete", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), analyzerPrefix = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @Transient
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public InputProvider htmlFullContentProvider;

    @KeywordField(name = "categories")
    public Set<String> categories = Set.of();

    @I18nFullTextField(name = "topics", analyzerPrefix = AnalysisConfigurer.DEFAULT, searchAnalyzerPrefix = AnalysisConfigurer.DEFAULT_SEARCH)
    @KeywordField(name = "topics_faceting", searchable = Searchable.YES, projectable = Projectable.YES, aggregable = Aggregable.YES)
    public Set<String> topics = Set.of();

    @KeywordField(name = "extensions_faceting", searchable = Searchable.YES, projectable = Projectable.YES, aggregable = Aggregable.YES)
    public Set<String> extensions = Set.of();

    /**
     * @return {@code true} if the guide is a Quarkus guide, {@code false} if this guide is a Quarkiverse guide.
     */
    public boolean quarkusGuide() {
        return QUARKUS_ORIGIN.equals(origin);
    }

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
