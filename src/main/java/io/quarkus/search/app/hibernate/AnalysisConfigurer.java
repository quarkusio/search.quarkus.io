package io.quarkus.search.app.hibernate;

import java.util.EnumSet;
import java.util.regex.Pattern;

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
            "vert.x, vertx, vertex",
            "configuration, config",
            "configuration properties, config properties, configuration options, config options"
    };

    public static final String DEFAULT = "basic_analyzer";
    public static final String DEFAULT_SEARCH = DEFAULT + "_search";
    public static final String AUTOCOMPLETE = "autocomplete";
    public static final String SORT = "sort";
    // This is simplified by assuming no default package, lowercase package names and capitalized class name,
    // so we get fewer false positives
    private static final Pattern SIMPLIFIED_JAVA_CLASS_NAME_CAPTURE_PATTERN = Pattern
            .compile("(?:[a-z_$][a-z0-9_$]*\\.)+([A-Z][A-Za-z0-9_$]*)$");

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
        // for en/es/pt we are going to use the same english configuration since guides are not translated
        EnumSet<Language> englishLanguages = EnumSet.of(Language.ENGLISH, Language.PORTUGUESE, Language.SPANISH);

        for (Language language : englishLanguages) {
            SharedFilters result = sharedFilters(context, language);

            // default:
            context.analyzer(defaultAnalyzer(language)).custom()
                    .tokenizer("standard")
                    .tokenFilters(result.possessiveStemmer(), result.stop(),
                            result.regularStemmer(),
                            result.compoundTechnicalNameFilter(),
                            "lowercase", "asciifolding")
                    .charFilters("html_strip");
            context.analyzer(defaultSearchAnalyzer(language)).custom()
                    .tokenizer("standard")
                    // > In general, synonym filters rewrite their inputs to the tokenizer and filters used in the preceding analysis chain
                    // Note how the synonym filter is added in the end. According to https://www.elastic.co/blog/boosting-the-power-of-elasticsearch-with-synonyms
                    // preceding filters should get applied to the synonyms we passed to it, so we don't need to bother about normalizing them in some way:
                    .tokenFilters(result.possessiveStemmer(), result.stop(),
                            result.regularStemmer(),
                            "lowercase", "asciifolding",
                            result.synonymsGraphFilter())
                    .charFilters("html_strip");

            // autocomplete:
            context.analyzer(autocompleteAnalyzer(language)).custom()
                    .tokenizer("standard")
                    .tokenFilters(result.possessiveStemmer(), result.stop(),
                            result.regularStemmer(),
                            result.compoundTechnicalNameFilter(),
                            "lowercase", "asciifolding",
                            result.autocompleteEdgeNgram())
                    .charFilters("html_strip");
        }

        // japanese
        // https://www.elastic.co/guide/en/elasticsearch/plugins/current/analysis-kuromoji-analyzer.html
        SharedFilters japanese = sharedFilters(context, Language.JAPANESE);
        context.analyzer(defaultAnalyzer(Language.JAPANESE)).custom()
                .tokenizer("kuromoji_tokenizer")
                .tokenFilters("kuromoji_baseform", "kuromoji_part_of_speech", japanese.possessiveStemmer(),
                        "ja_stop", japanese.stop(), "kuromoji_stemmer", japanese.regularStemmer(),
                        japanese.compoundTechnicalNameFilter(),
                        "lowercase", "asciifolding")
                .charFilters("icu_normalizer", "html_strip");
        context.analyzer(defaultSearchAnalyzer(Language.JAPANESE)).custom()
                .tokenizer("kuromoji_tokenizer")
                // > In general, synonym filters rewrite their inputs to the tokenizer and filters used in the preceding analysis chain
                // Note how the synonym filter is added in the end. According to https://www.elastic.co/blog/boosting-the-power-of-elasticsearch-with-synonyms
                // preceding filters should get applied to the synonyms we passed to it, so we don't need to bother about normalizing them in some way:
                .tokenFilters("kuromoji_baseform", "kuromoji_part_of_speech", japanese.possessiveStemmer(),
                        "ja_stop", japanese.stop(), "kuromoji_stemmer", japanese.regularStemmer(),
                        "lowercase", "asciifolding",
                        japanese.synonymsGraphFilter())
                .charFilters("icu_normalizer", "html_strip");

        // autocomplete:
        context.analyzer(autocompleteAnalyzer(Language.JAPANESE)).custom()
                .tokenizer("kuromoji_tokenizer")
                .tokenFilters("kuromoji_baseform", "kuromoji_part_of_speech", japanese.possessiveStemmer(),
                        "ja_stop", japanese.stop(), "kuromoji_stemmer", japanese.regularStemmer(),
                        japanese.compoundTechnicalNameFilter(),
                        "lowercase", "asciifolding",
                        japanese.autocompleteEdgeNgram())
                .charFilters("icu_normalizer", "html_strip");

        // chinese
        // https://www.elastic.co/guide/en/elasticsearch/plugins/current/_reimplementing_and_extending_the_analyzers.html
        SharedFilters chinese = sharedFilters(context, Language.CHINESE);
        context.analyzer(defaultAnalyzer(Language.CHINESE)).custom()
                .tokenizer("smartcn_tokenizer")
                .tokenFilters(chinese.possessiveStemmer(), "smartcn_stop", chinese.stop(),
                        chinese.regularStemmer(), chinese.compoundTechnicalNameFilter(),
                        "lowercase", "asciifolding")
                .charFilters("html_strip");
        context.analyzer(defaultSearchAnalyzer(Language.CHINESE)).custom()
                .tokenizer("smartcn_tokenizer")
                // > In general, synonym filters rewrite their inputs to the tokenizer and filters used in the preceding analysis chain
                // Note how the synonym filter is added in the end. According to https://www.elastic.co/blog/boosting-the-power-of-elasticsearch-with-synonyms
                // preceding filters should get applied to the synonyms we passed to it, so we don't need to bother about normalizing them in some way.
                //
                // NOTE: see how `smartcn_stop` filter goes after the synonyms, placing it next to the regular stop filter leads to
                // startup failures, as schema cannot be created.
                .tokenFilters(chinese.possessiveStemmer(), chinese.stop(),
                        chinese.regularStemmer(),
                        "lowercase", "asciifolding",
                        chinese.synonymsGraphFilter(), "smartcn_stop")
                .charFilters("html_strip");

        // autocomplete:
        context.analyzer(autocompleteAnalyzer(Language.CHINESE)).custom()
                .tokenizer("smartcn_tokenizer")
                .tokenFilters(chinese.possessiveStemmer(), "smartcn_stop", chinese.stop(),
                        chinese.regularStemmer(), chinese.compoundTechnicalNameFilter(),
                        "lowercase", "asciifolding",
                        chinese.autocompleteEdgeNgram())
                .charFilters("html_strip");

        context.normalizer(SORT).custom()
                .tokenFilters("lowercase");
    }

    private static SharedFilters sharedFilters(ElasticsearchAnalysisConfigurationContext context, Language language) {
        String stop = "stop_%s".formatted(language.code);
        String regularStemmer = "stemmer_%s".formatted(language.code);
        String possessiveStemmer = "possessive_stemmer_%s".formatted(language.code);
        String autocompleteEdgeNgram = "autocomplete_edge_ngram_%s".formatted(language.code);
        String synonymsGraphFilter = "synonyms_graph_filter_%s".formatted(language.code);
        String compoundTechnicalNameFilter = "compound_technical_name_filter_%s".formatted(language.code);
        context.tokenFilter(stop)
                .type("stop")
                .param("stopwords", "_english_")
                .param("ignore_case", "true");
        context.tokenFilter(regularStemmer)
                .type("stemmer")
                .param("language", "english");
        context.tokenFilter(possessiveStemmer)
                .type("stemmer")
                .param("language", "possessive_english");
        context.tokenFilter(autocompleteEdgeNgram)
                .type("edge_ngram")
                .param("min_gram", 2)
                .param("max_gram", 70);
        context.tokenFilter(synonymsGraphFilter)
                // See https://www.elastic.co/guide/en/elasticsearch/reference/8.11/analysis-synonym-graph-tokenfilter.html#analysis-synonym-graph-tokenfilter
                // synonym_graph works better with multi-word synonyms
                .type("synonym_graph")
                .param("synonyms", SYNONYMS);
        context.tokenFilter(compoundTechnicalNameFilter)
                .type("pattern_capture")
                .param("preserve_original", true)
                // This decomposes io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem into
                // [
                // io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem,
                // AdditionalIndexedClassesBuildItem
                // ]
                .param("patterns", SIMPLIFIED_JAVA_CLASS_NAME_CAPTURE_PATTERN.pattern());
        return new SharedFilters(stop, regularStemmer, possessiveStemmer, autocompleteEdgeNgram,
                synonymsGraphFilter, compoundTechnicalNameFilter);
    }

    private record SharedFilters(String stop, String regularStemmer, String possessiveStemmer, String autocompleteEdgeNgram,
            String synonymsGraphFilter, String compoundTechnicalNameFilter) {
    }
}
