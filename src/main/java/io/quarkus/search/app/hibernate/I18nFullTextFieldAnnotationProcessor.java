package io.quarkus.search.app.hibernate;

import java.util.EnumMap;
import java.util.Map;
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

    // The (incubating) AlternativeBinder doesn't handle generics like Map<..., T> at the moment.
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
                .binder(Map.class.isAssignableFrom(context.annotatedElement().javaClass())
                        ? AlternativeBinder.create(
                                Language.class,
                                context.annotatedElement().name(),
                                Map.class,
                                beanResolver -> BeanHolder.of(LanguageAlternativeBinderDelegate.forMap(annotation,
                                        valueBridgeRef.resolve(beanResolver).get())))
                        : AlternativeBinder.create(
                                Language.class,
                                context.annotatedElement().name(),
                                Object.class,
                                beanResolver -> BeanHolder.of(LanguageAlternativeBinderDelegate.forSingle(annotation,
                                        valueBridgeRef.resolve(beanResolver).get()))));
    }

    static class LanguageAlternativeBinderDelegate<T>
            implements AlternativeBinderDelegate<Language, T> {

        static <V> AlternativeBinderDelegate<Language, V> forSingle(
                I18nFullTextField annotation, ValueBridge<V, String> valueBridge) {
            return new LanguageAlternativeBinderDelegate<>(
                    annotation, fields -> new SingleValueBridge<>(valueBridge, fields));
        }

        // The (incubating) AlternativeBinder doesn't handle generics like Map<..., T> at the moment.
        @SuppressWarnings({ "rawtypes", "unchecked" })
        static <V> AlternativeBinderDelegate<Language, Map> forMap(
                I18nFullTextField annotation, ValueBridge<V, String> valueBridge) {
            return new LanguageAlternativeBinderDelegate<>(
                    annotation, (LanguageBridgeFactory) fields -> new MapBridge<>(valueBridge, fields));
        }

        private final String name;
        private final String analyzerPrefix;
        private final String searchAnalyzerPrefix;
        private final TermVector termVector;
        private final Set<Highlightable> highlightable;
        private final LanguageBridgeFactory<T> bridgeFactory;

        private LanguageAlternativeBinderDelegate(I18nFullTextField annotation,
                LanguageBridgeFactory<T> bridgeFactory) {
            this.name = annotation.name().isEmpty() ? null : annotation.name();
            this.analyzerPrefix = annotation.analyzerPrefix();
            this.searchAnalyzerPrefix = annotation.searchAnalyzerPrefix();
            this.termVector = annotation.termVector();
            this.highlightable = Set.of(annotation.highlightable());
            this.bridgeFactory = bridgeFactory;
        }

        @Override
        public AlternativeValueBridge<Language, T> bind(IndexSchemaElement indexSchemaElement,
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

            return bridgeFactory.create(fields);
        }

        private interface LanguageBridgeFactory<T> {
            AlternativeValueBridge<Language, T> create(EnumMap<Language, IndexFieldReference<String>> fields);
        }

        private static class SingleValueBridge<V> implements AlternativeValueBridge<Language, V> {

            private final ValueBridge<V, String> delegate;
            private final EnumMap<Language, IndexFieldReference<String>> fields;

            private SingleValueBridge(ValueBridge<V, String> delegate, EnumMap<Language, IndexFieldReference<String>> fields) {
                this.delegate = delegate;
                this.fields = fields;
            }

            @Override
            public void write(DocumentElement target, Language language, V bridgedElement) {
                if (language != null) {
                    target.addValue(fields.get(language), delegate.toIndexedValue(bridgedElement, null));
                } else {
                    // No language: this happens for Quarkiverse guides in particular.
                    // Just populate all language fields with the same value.
                    for (IndexFieldReference<String> field : fields.values()) {
                        target.addValue(field, delegate.toIndexedValue(bridgedElement, null));
                    }
                }
            }
        }

        private static class MapBridge<V> implements AlternativeValueBridge<Language, Map<Language, V>> {

            private final ValueBridge<V, String> delegate;
            private final EnumMap<Language, IndexFieldReference<String>> fields;

            private MapBridge(ValueBridge<V, String> delegate, EnumMap<Language, IndexFieldReference<String>> fields) {
                this.delegate = delegate;
                this.fields = fields;
            }

            @Override
            public void write(DocumentElement target, Language language, Map<Language, V> bridgedElement) {
                if (language != null) {
                    target.addValue(fields.get(language), delegate.toIndexedValue(bridgedElement.get(language), null));
                } else {
                    // No language: this happens for Quarkiverse guides in particular.
                    // Just populate all language fields with the corresponding value from the map.
                    for (Map.Entry<Language, IndexFieldReference<String>> entry : fields.entrySet()) {
                        language = entry.getKey();
                        var field = entry.getValue();
                        target.addValue(field, delegate.toIndexedValue(bridgedElement.get(language), null));
                    }
                }
            }
        }
    }
}
