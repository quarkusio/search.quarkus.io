package io.quarkus.search.app.entity;

import io.quarkus.search.app.hibernate.InputProvider;
import io.quarkus.search.app.hibernate.InputProviderHtmlBodyTextBridge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.Set;

import org.hibernate.Length;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
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
    public String path;

    @KeywordField
    public String version;

    @FullTextField
    @FullTextField(name = "title_autocomplete", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT)
    @KeywordField(name = "title_sort", normalizer = AnalysisConfigurer.SORT, searchable = Searchable.NO, sortable = Sortable.YES)
    @Column(length = Length.LONG)
    public String title;

    @FullTextField
    @FullTextField(name = "summary_autocomplete", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT)
    @Column(length = Length.LONG32)
    public String summary;

    @FullTextField
    @FullTextField(name = "keywords_autocomplete", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT)
    @Column(length = Length.LONG32)
    public String keywords;

    @FullTextField(name = "fullContent", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class))
    @FullTextField(name = "fullContent_autocomplete", valueBridge = @ValueBridgeRef(type = InputProviderHtmlBodyTextBridge.class), analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT)
    @Transient
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public InputProvider htmlFullContentProvider;

    @KeywordField(name = "categories")
    public Set<String> categories = Set.of();

    @FullTextField(name = "topics")
    @KeywordField(name = "topics_faceting", searchable = Searchable.YES, projectable = Projectable.YES, aggregable = Aggregable.YES)
    public Set<String> topics = Set.of();

    @KeywordField(name = "extensions_faceting", searchable = Searchable.YES, projectable = Projectable.YES, aggregable = Aggregable.YES)
    public Set<String> extensions = Set.of();

    @Override
    public String toString() {
        return "Guide{" +
                "path='" + path + '\'' +
                '}';
    }
}
