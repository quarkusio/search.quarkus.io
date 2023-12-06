package io.quarkus.search.app.hibernate;

import io.quarkus.search.app.entity.Language;

import io.quarkus.hibernate.search.orm.elasticsearch.SearchExtension;

import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurationContext;
import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurer;

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

    public static String defaultAnalyzer(Language language) {
        return localizedAnalyzer(DEFAULT, language);
    }

    public static String defaultSearchAnalyzer(Language language) {
        return localizedAnalyzer(DEFAULT_SEARCH, language);
    }

    public static String autocompleteAnalyzer(Language language) {
        return localizedAnalyzer(AUTOCOMPLETE, language);
    }

    public static String localizedAnalyzer(String prefix, Language language) {
        return "%s_%s".formatted(prefix, language.code);
    }

    @Override
    public void configure(ElasticsearchAnalysisConfigurationContext context) {
        // just to have something for app to start correctly:
        for (Language language : Language.values()) {
            context.analyzer(defaultAnalyzer(language)).custom()
                    .tokenizer("standard")
                    .tokenFilters("lowercase", "asciifolding", "stemmer")
                    .charFilters("html_strip");

            String synonymsGraphFilter = "synonyms_graph_filter_%s".formatted(language.code);
            context.analyzer(defaultSearchAnalyzer(language)).custom()
                    .tokenizer("standard")
                    // > In general, synonym filters rewrite their inputs to the tokenizer and filters used in the preceding analysis chain
                    // Note how the synonym filter is added in the end. According to https://www.elastic.co/blog/boosting-the-power-of-elasticsearch-with-synonyms
                    // preceding filters should get applied to the synonyms we passed to it, so we don't need to bother about normalizing them in some way:
                    .tokenFilters("lowercase", "asciifolding", "stemmer", synonymsGraphFilter)
                    .charFilters("html_strip");
            context.tokenFilter(synonymsGraphFilter)
                    // See https://www.elastic.co/guide/en/elasticsearch/reference/8.11/analysis-synonym-graph-tokenfilter.html#analysis-synonym-graph-tokenfilter
                    // synonym_graph works better with multi-word synonyms
                    .type("synonym_graph")
                    .param("synonyms", SYNONYMS);

            String autocompleteEdgeNgram = "autocomplete_edge_ngram_%s".formatted(language.code);
            context.analyzer(autocompleteAnalyzer(language)).custom()
                    .tokenizer("standard")
                    .tokenFilters("lowercase", "asciifolding", "stemmer", autocompleteEdgeNgram)
                    .charFilters("html_strip");
            context.tokenFilter(autocompleteEdgeNgram)
                    .type("edge_ngram")
                    .param("min_gram", 1)
                    .param("max_gram", 10);
        }
        context.normalizer(SORT).custom()
                .tokenFilters("lowercase");
    }
}
