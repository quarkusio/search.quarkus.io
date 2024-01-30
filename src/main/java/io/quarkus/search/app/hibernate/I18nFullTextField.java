package io.quarkus.search.app.hibernate;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

import io.quarkus.search.app.entity.Language;

import org.hibernate.search.engine.backend.analysis.AnalyzerNames;
import org.hibernate.search.engine.backend.types.Highlightable;
import org.hibernate.search.engine.backend.types.TermVector;
import org.hibernate.search.engine.environment.bean.BeanReference;
import org.hibernate.search.engine.environment.bean.BeanRetrieval;
import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.ValueBridgeRef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.PropertyMapping;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.PropertyMappingAnnotationProcessor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.PropertyMappingAnnotationProcessorContext;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.PropertyMappingAnnotationProcessorRef;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.PropertyMappingStep;

@Documented
@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(I18nFullTextField.List.class)
@PropertyMapping(processor = @PropertyMappingAnnotationProcessorRef(type = I18nFullTextField.Processor.class, retrieval = BeanRetrieval.CONSTRUCTOR))
public @interface I18nFullTextField {

    String name() default "";

    String analyzerPrefix() default AnalyzerNames.DEFAULT;

    String searchAnalyzerPrefix() default "";

    Highlightable[] highlightable() default { Highlightable.DEFAULT };

    TermVector termVector() default TermVector.DEFAULT;

    ValueBridgeRef valueBridge() default @ValueBridgeRef;

    @Documented
    @Target({ ElementType.METHOD, ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        I18nFullTextField[] value();
    }

    class Processor implements PropertyMappingAnnotationProcessor<I18nFullTextField> {
        @SuppressWarnings("unchecked") // Allowing raw types for legacy reasons (see ValueBridgeRef)
        @Override
        public void process(PropertyMappingStep mapping, I18nFullTextField annotation,
                PropertyMappingAnnotationProcessorContext context) {
            BeanReference<? extends ValueBridge<?, String>> valueBridgeRef = (BeanReference<? extends ValueBridge<?, String>>) context
                    .toBeanReference(ValueBridge.class,
                            ValueBridgeRef.UndefinedBridgeImplementationType.class,
                            annotation.valueBridge().type(), annotation.valueBridge().name())
                    .orElseGet(() -> BeanReference.ofInstance((value, ctx) -> value));

            String fieldNamePrefix = annotation.name().isEmpty() ? context.annotatedElement().name() : annotation.name();

            // Create one field per language, populated from the relevant data in I18nData
            for (Language language : Language.values()) {
                mapping.fullTextField(language.addSuffix(fieldNamePrefix))
                        .valueBinder(new I18nDataBinder(language, valueBridgeRef))
                        .termVector(annotation.termVector())
                        .highlightable(Set.of(annotation.highlightable()))
                        .analyzer(language.addSuffix(annotation.analyzerPrefix()))
                        .searchAnalyzer(language.addSuffix(annotation.searchAnalyzerPrefix()));
            }
        }

    }

}
