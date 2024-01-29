package io.quarkus.search.app.hibernate;

import java.util.EnumMap;
import java.util.Objects;
import java.util.Set;

import io.quarkus.search.app.entity.Language;

import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.types.Highlightable;
import org.hibernate.search.engine.backend.types.TermVector;
import org.hibernate.search.engine.environment.bean.BeanHolder;
import org.hibernate.search.engine.environment.bean.BeanReference;
import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.builtin.programmatic.AlternativeBinder;
import org.hibernate.search.mapper.pojo.bridge.builtin.programmatic.AlternativeBinderDelegate;
import org.hibernate.search.mapper.pojo.bridge.builtin.programmatic.AlternativeValueBridge;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.ValueBridgeRef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.PropertyMappingAnnotationProcessor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.PropertyMappingAnnotationProcessorContext;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.PropertyMappingStep;
import org.hibernate.search.mapper.pojo.model.PojoModelProperty;

public class I18nFullTextFieldAnnotationProcessor implements PropertyMappingAnnotationProcessor<I18nFullTextField> {

    @SuppressWarnings("unchecked")
    @Override
    public void process(PropertyMappingStep mapping, I18nFullTextField annotation,
            PropertyMappingAnnotationProcessorContext context) {
        BeanReference<? extends ValueBridge<Object, String>> valueBridgeRef = (BeanReference<? extends ValueBridge<Object, String>>) context
                .toBeanReference(ValueBridge.class,
                        ValueBridgeRef.UndefinedBridgeImplementationType.class,
                        annotation.valueBridge().type(), annotation.valueBridge().name())
                .orElseGet(() -> BeanReference.ofInstance((value, ctx) -> Objects.toString(value, null)));

        mapping.hostingType()
                .binder(AlternativeBinder.create(
                        Language.class,
                        context.annotatedElement().name(),
                        Object.class,
                        beanResolver -> BeanHolder.of(new LanguageAlternativeBinderDelegate<>(
                                annotation.name().isEmpty() ? null : annotation.name(),
                                annotation.analyzerPrefix(),
                                annotation.searchAnalyzerPrefix(),
                                annotation.termVector(),
                                annotation.highlightable(),
                                beanResolver.resolve(valueBridgeRef)))));
    }

    public static class LanguageAlternativeBinderDelegate<S> implements AlternativeBinderDelegate<Language, S> {

        private final String name;
        private final String analyzerPrefix;
        private final String searchAnalyzerPrefix;
        private final TermVector termVector;
        private final Set<Highlightable> highlightable;
        private final ValueBridge<S, String> bridge;

        public LanguageAlternativeBinderDelegate(String name, String analyzerPrefix, String searchAnalyzerPrefix,
                TermVector termVector,
                Highlightable[] highlightable, BeanHolder<? extends ValueBridge<S, String>> bridge) {
            this.name = name;
            this.analyzerPrefix = analyzerPrefix;
            this.searchAnalyzerPrefix = searchAnalyzerPrefix;
            this.termVector = termVector;
            this.highlightable = Set.of(highlightable);
            this.bridge = bridge.get();
        }

        @Override
        public AlternativeValueBridge<Language, S> bind(IndexSchemaElement indexSchemaElement,
                PojoModelProperty fieldValueSource) {
            EnumMap<Language, IndexFieldReference<String>> fields = new EnumMap<>(Language.class);
            String fieldNamePrefix = (name != null ? name : fieldValueSource.name()) + "_";

            for (Language language : Language.values()) {
                String languageCode = language.code;
                IndexFieldReference<String> field = indexSchemaElement.field(
                        fieldNamePrefix + languageCode,
                        f -> f.asString()
                                .termVector(termVector)
                                .highlightable(highlightable)
                                .analyzer(AnalysisConfigurer.localizedAnalyzer(analyzerPrefix, language))
                                .searchAnalyzer(AnalysisConfigurer.localizedAnalyzer(searchAnalyzerPrefix, language)))
                        .toReference();
                fields.put(language, field);
            }

            return new Bridge<>(bridge, fields);
        }

        private static class Bridge<S> implements AlternativeValueBridge<Language, S> {

            private final ValueBridge<S, String> bridge;
            private final EnumMap<Language, IndexFieldReference<String>> fields;

            private Bridge(ValueBridge<S, String> bridge, EnumMap<Language, IndexFieldReference<String>> fields) {
                this.bridge = bridge;
                this.fields = fields;
            }

            @Override
            public void write(DocumentElement target, Language language, S bridgedElement) {
                if (language != null) {
                    target.addValue(fields.get(language), bridge.toIndexedValue(bridgedElement, null));
                } else {
                    // No language: this happens for Quarkiverse guides in particular.
                    // Just populate all language fields with the same value.
                    for (IndexFieldReference<String> field : fields.values()) {
                        target.addValue(field, bridge.toIndexedValue(bridgedElement, null));
                    }
                }
            }
        }
    }
}
