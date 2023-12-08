package io.quarkus.search.app.hibernate;

import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurationContext;
import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurer;

import io.quarkus.hibernate.search.orm.elasticsearch.SearchExtension;

@SearchExtension
public class AnalysisConfigurer implements ElasticsearchAnalysisConfigurer {

    private static final String[] SYNONYMS = new String[] {
            "development, dev",
            "dev service, devservice, development service",
            "resteasy, rest, rest api, rest easy",
            "vert.x, vertx, vertex"
    };

    public static final String DEFAULT = "basic_analyzer";
    public static final String DEFAULT_SEARCH = DEFAULT + "_search";
    public static final String AUTOCOMPLETE = "autocomplete";
    public static final String SORT = "sort";

    @Override
    public void configure(ElasticsearchAnalysisConfigurationContext context) {
        context.analyzer(DEFAULT).custom()
                .tokenizer("standard")
                .tokenFilters("lowercase", "asciifolding", "stemmer")
                .charFilters("html_strip");

        context.analyzer(DEFAULT_SEARCH).custom()
                .tokenizer("standard")
                // > In general, synonym filters rewrite their inputs to the tokenizer and filters used in the preceding analysis chain
                // Note how the synonym filter is added in the end. According to https://www.elastic.co/blog/boosting-the-power-of-elasticsearch-with-synonyms
                // preceding filters should get applied to the synonyms we passed to it, so we don't need to bother about normalizing them in some way:
                .tokenFilters("lowercase", "asciifolding", "stemmer", "synonyms_graph_filter")
                .charFilters("html_strip");
        context.tokenFilter("synonyms_graph_filter")
                // See https://www.elastic.co/guide/en/elasticsearch/reference/8.11/analysis-synonym-graph-tokenfilter.html#analysis-synonym-graph-tokenfilter
                // synonym_graph works better with multi-word synonyms
                .type("synonym_graph")
                .param("synonyms", SYNONYMS);

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
