package io.quarkus.search.app.entity;

import io.quarkus.search.app.hibernate.I18nFullTextField;
import io.quarkus.search.app.hibernate.I18nKeywordField;
import io.quarkus.search.app.hibernate.InputProvider;
import io.quarkus.search.app.hibernate.InputProviderHtmlBodyTextBridge;
import io.quarkus.search.app.hibernate.Localization;
import io.quarkus.search.app.hibernate.URIType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

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

import io.quarkus.search.app.hibernate.AnalysisConfigurer;
import jakarta.persistence.Transient;

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

    @I18nFullTextField(highlightable = Highlightable.UNIFIED, termVector = TermVector.WITH_POSITIONS_OFFSETS, localization = {
            @Localization(language = "en"),
            @Localization(language = "es"),
            @Localization(language = "ja")
    })
    @I18nFullTextField(name = "title_autocomplete", localization = {
            @Localization(language = "en", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT),
            @Localization(language = "es", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT),
            @Localization(language = "ja", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT)
    })
    @I18nKeywordField(name = "title_sort", normalizer = AnalysisConfigurer.SORT, searchable = Searchable.NO, sortable = Sortable.YES, languages = {
            "en", "es", "ja" })
    @Embedded
    @AttributeOverride(name = "data", column = @Column(length = Length.LONG, name = "title"))
    public I18nData<String> title;

    @I18nFullTextField(highlightable = Highlightable.UNIFIED, termVector = TermVector.WITH_POSITIONS_OFFSETS, localization = {
            @Localization(language = "en"),
            @Localization(language = "es"),
            @Localization(language = "ja")
    })
    @I18nFullTextField(name = "summary_autocomplete", localization = {
            @Localization(language = "en", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT),
            @Localization(language = "es", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT),
            @Localization(language = "ja", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT)
    })
    @Embedded
    @AttributeOverride(name = "data", column = @Column(length = Length.LONG32, name = "summary"))
    public I18nData<String> summary;

    @FullTextField
    @FullTextField(name = "keywords_autocomplete", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT)
    @Column(length = Length.LONG32)
    public String keywords;

    @Transient
    @I18nFullTextField(name = "fullContent", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), highlightable = Highlightable.UNIFIED, localization = {
            @Localization(language = "en"),
            @Localization(language = "es"),
            @Localization(language = "ja")
    })
    @I18nFullTextField(name = "fullContent_autocomplete", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), localization = {
            @Localization(language = "en", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT),
            @Localization(language = "es", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT),
            @Localization(language = "ja", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT)
    })
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public I18nData<InputProvider> htmlFullContentProvider;

    @KeywordField(name = "categories")
    public Set<String> categories = Set.of();

    @FullTextField(name = "topics")
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
