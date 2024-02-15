package io.quarkus.search.app.hibernate;

import java.util.Arrays;
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
        return language.addSuffix(DEFAULT);
    }

    public static String defaultSearchAnalyzer(Language language) {
        return language.addSuffix(DEFAULT_SEARCH);
    }

    public static String autocompleteAnalyzer(Language language) {
        return language.addSuffix(AUTOCOMPLETE);
    }

    private static String stopFilter(Language language) {
        return "stop_%s".formatted(language.code);
    }

    private static String regularStemmerFilter(Language language) {
        return "stemmer_%s".formatted(language.code);
    }

    private static String possessiveStemmerFilter(Language language) {
        return "possessive_stemmer_%s".formatted(language.code);
    }

    private static String autocompleteEdgeNgramFilter(Language language) {
        return "autocomplete_edge_ngram_%s".formatted(language.code);
    }

    private static String synonymsGraphFilter(Language language) {
        return "synonyms_graph_filter_%s".formatted(language.code);
    }

    private static String compoundTechnicalNameFilter(Language language) {
        return "compound_technical_name_filter_%s".formatted(language.code);
    }

    @Override
    public void configure(ElasticsearchAnalysisConfigurationContext context) {
        // for en/es/pt we are going to use the same english configuration since guides are not translated
        EnumSet<Language> englishLanguages = EnumSet.of(Language.ENGLISH, Language.PORTUGUESE, Language.SPANISH);

        for (Language language : englishLanguages) {
            configure(
                    context,
                    language,
                    "standard",
                    // char filters:
                    new String[] { "html_strip" },
                    // token filters:
                    new String[] {
                            // A pattern capture filter to address FQCNs
                            compoundTechnicalNameFilter(language),
                            // To make all words in lowercase.
                            "lowercase",
                            // To remove possessives (trailing 's) from words.
                            possessiveStemmerFilter(language),
                            // To remove frequently used words that do not bring much meaning, e.g. a, that, and, are, as, at, with...
                            stopFilter(language),
                            // To remove suffixes like -s/-es/-ed etc
                            regularStemmerFilter(language),
                            // To convert characters into ascii ones, e.g. à to a or ę to e etc.
                            "asciifolding"
                    },
                    //  search token filters:
                    new String[] {
                            "lowercase",
                            possessiveStemmerFilter(language),
                            stopFilter(language),
                            regularStemmerFilter(language),
                            "asciifolding",
                            // > In general, synonym filters rewrite their inputs to the tokenizer and filters used in the preceding analysis chain
                            // Note how the synonym filter is added in the end. According to https://www.elastic.co/blog/boosting-the-power-of-elasticsearch-with-synonyms
                            // preceding filters should get applied to the synonyms we passed to it, so we don't need to bother about normalizing them in some way:
                            synonymsGraphFilter(language)
                    });
        }

        // Japanese
        // https://www.elastic.co/guide/en/elasticsearch/plugins/current/analysis-kuromoji-analyzer.html
        configure(
                context,
                Language.JAPANESE,
                "kuromoji_tokenizer",
                // char filters:
                new String[] {
                        // If a text contains full-width characters, the kuromoji_tokenizer tokenizer can produce unexpected tokens.
                        // To avoid this, the icu_normalizer character filter is added.
                        // Requires icu plugin being installed.
                        "icu_normalizer",
                        "html_strip"
                },
                // token filters:
                new String[] {
                        compoundTechnicalNameFilter(Language.JAPANESE),
                        "lowercase",
                        "kuromoji_baseform",
                        "kuromoji_part_of_speech",
                        possessiveStemmerFilter(Language.JAPANESE),
                        "ja_stop",
                        stopFilter(Language.JAPANESE),
                        "kuromoji_stemmer",
                        regularStemmerFilter(Language.JAPANESE),
                        "asciifolding"
                },
                //  search token filters:
                new String[] {
                        "lowercase",
                        "kuromoji_baseform",
                        "kuromoji_part_of_speech",
                        possessiveStemmerFilter(Language.JAPANESE),
                        "ja_stop",
                        stopFilter(Language.JAPANESE),
                        "kuromoji_stemmer",
                        regularStemmerFilter(Language.JAPANESE),
                        "asciifolding",
                        synonymsGraphFilter(Language.JAPANESE)
                });

        // Chinese
        // https://www.elastic.co/guide/en/elasticsearch/plugins/current/_reimplementing_and_extending_the_analyzers.html
        configure(
                context,
                Language.CHINESE,
                "smartcn_tokenizer",
                // char filters:
                new String[] { "html_strip" },
                // token filters:
                new String[] {
                        compoundTechnicalNameFilter(Language.CHINESE),
                        "lowercase",
                        possessiveStemmerFilter(Language.CHINESE),
                        "smartcn_stop",
                        stopFilter(Language.CHINESE),
                        regularStemmerFilter(Language.CHINESE),
                        "asciifolding"
                },
                //  search token filters:
                new String[] {
                        "lowercase",
                        possessiveStemmerFilter(Language.CHINESE),
                        stopFilter(Language.CHINESE),
                        regularStemmerFilter(Language.CHINESE),
                        "asciifolding",
                        synonymsGraphFilter(Language.CHINESE),
                        // must go last as it conflicts wit the synonyms filter.
                        "smartcn_stop"
                });
    }

    private static void configure(
            ElasticsearchAnalysisConfigurationContext context,
            Language language,
            String tokenizer,
            String[] charFilters,
            String[] tokenFilters,
            String[] searchTokenFilters) {

        configureTokenFilters(context, language);

        // The default analyzer to prepare tokens for when the search is performed for the full words.
        // To be applied to the indexed text.
        context.analyzer(defaultAnalyzer(language)).custom()
                .tokenizer(tokenizer)
                .tokenFilters(tokenFilters)
                .charFilters(charFilters);

        // The analyzer to be applied to the user-input text.
        context.analyzer(defaultSearchAnalyzer(language)).custom()
                .tokenizer(tokenizer)
                .tokenFilters(searchTokenFilters)
                .charFilters(charFilters);

        // The autocomplete analyzer to prepare tokens for when the user types the words,
        //  and we want to produce results on incompletely typed (unfinished) words.
        //  To get that we add an edge Ngram filter in the end of the list of filters of the default analyzer
        //  to make sure that we get the same tokens processed into Ngrams.
        //  To be applied to the indexed text.
        String[] autocompleteTokenFilters = Arrays.copyOf(tokenFilters, tokenFilters.length + 1);
        autocompleteTokenFilters[tokenFilters.length] = autocompleteEdgeNgramFilter(language);
        context.analyzer(autocompleteAnalyzer(language)).custom()
                .tokenizer(tokenizer)
                .tokenFilters(autocompleteTokenFilters)
                .charFilters(charFilters);

        context.normalizer(language.addSuffix(SORT)).custom()
                .tokenFilters("lowercase");
    }

    private static void configureTokenFilters(ElasticsearchAnalysisConfigurationContext context, Language language) {
        context.tokenFilter(stopFilter(language))
                .type("stop")
                .param("stopwords", "_english_")
                .param("ignore_case", "true");
        context.tokenFilter(regularStemmerFilter(language))
                .type("stemmer")
                .param("language", "english");
        context.tokenFilter(possessiveStemmerFilter(language))
                .type("stemmer")
                .param("language", "possessive_english");
        context.tokenFilter(autocompleteEdgeNgramFilter(language))
                .type("edge_ngram")
                .param("min_gram", 2)
                .param("max_gram", 70);
        context.tokenFilter(synonymsGraphFilter(language))
                // See https://www.elastic.co/guide/en/elasticsearch/reference/8.11/analysis-synonym-graph-tokenfilter.html#analysis-synonym-graph-tokenfilter
                // synonym_graph works better with multi-word synonyms
                .type("synonym_graph")
                .param("synonyms", SYNONYMS);
        context.tokenFilter(compoundTechnicalNameFilter(language))
                .type("pattern_capture")
                .param("preserve_original", true)
                // This decomposes io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem into
                // [
                // io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem,
                // AdditionalIndexedClassesBuildItem
                // ]
                .param("patterns", SIMPLIFIED_JAVA_CLASS_NAME_CAPTURE_PATTERN.pattern());
    }

}
