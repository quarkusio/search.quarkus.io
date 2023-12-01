package io.quarkus.search.app.hibernate;

import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurationContext;
import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurer;
import org.hibernate.search.engine.backend.analysis.AnalyzerNames;

import io.quarkus.hibernate.search.orm.elasticsearch.SearchExtension;

@SearchExtension
public class AnalysisConfigurer implements ElasticsearchAnalysisConfigurer {
    public static final String DEFAULT = AnalyzerNames.DEFAULT;
    public static final String AUTOCOMPLETE = "autocomplete";
    public static final String SORT = "sort";

    @Override
    public void configure(ElasticsearchAnalysisConfigurationContext context) {
        context.analyzer(DEFAULT).custom()
                .tokenizer("standard")
                .tokenFilters("lowercase", "asciifolding", "stemmer")
                .charFilters("html_strip");
        context.analyzer(AUTOCOMPLETE).custom()
                .tokenizer("standard")
                .tokenFilters("lowercase", "asciifolding", "stemmer", "autocomplete_edge_ngram")
                .charFilters("html_strip");
        context.tokenFilter("autocomplete_edge_ngram")
                .type("edge_ngram")
                .param("min_gram", 1)
                .param("max_gram", 10);
        context.normalizer(SORT).custom()
                .tokenFilters("lowercase");
    }
}
