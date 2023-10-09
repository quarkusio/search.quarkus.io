package io.quarkus.search.app.entity;

import io.quarkus.search.app.hibernate.AnalysisConfigurer;
import io.quarkus.search.app.hibernate.PathWrapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.Length;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

@Entity
@Indexed
public class Guide {
    @Id
    public String relativePath;

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

    @FullTextField(name = "fullContent")
    @FullTextField(name = "fullContent_autocomplete", analyzer = AnalysisConfigurer.AUTOCOMPLETE, searchAnalyzer = AnalysisConfigurer.DEFAULT)
    @Column(length = Length.LONG)
    // Using PathWrapper because of https://hibernate.atlassian.net/browse/HSEARCH-4988
    public PathWrapper fullContentPath;

}
