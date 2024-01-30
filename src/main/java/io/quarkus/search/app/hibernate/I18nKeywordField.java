package io.quarkus.search.app.hibernate;

import static io.quarkus.search.app.entity.Language.localizedName;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.quarkus.search.app.entity.Language;

import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
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
@Repeatable(I18nKeywordField.List.class)
@PropertyMapping(processor = @PropertyMappingAnnotationProcessorRef(type = I18nKeywordField.Processor.class, retrieval = BeanRetrieval.CONSTRUCTOR))
public @interface I18nKeywordField {

    String name() default "";

    String normalizerPrefix() default "";

    Searchable searchable() default Searchable.DEFAULT;

    Sortable sortable() default Sortable.DEFAULT;

    Projectable projectable() default Projectable.DEFAULT;

    Aggregable aggregable() default Aggregable.DEFAULT;

    ValueBridgeRef valueBridge() default @ValueBridgeRef;

    @Documented
    @Target({ ElementType.METHOD, ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        I18nKeywordField[] value();
    }

    class Processor implements PropertyMappingAnnotationProcessor<I18nKeywordField> {
        @SuppressWarnings("unchecked") // Allowing raw types for legacy reasons (see ValueBridgeRef)
        @Override
        public void process(PropertyMappingStep mapping, I18nKeywordField annotation,
                PropertyMappingAnnotationProcessorContext context) {
            BeanReference<? extends ValueBridge<?, String>> valueBridgeRef = (BeanReference<? extends ValueBridge<?, String>>) context
                    .toBeanReference(ValueBridge.class,
                            ValueBridgeRef.UndefinedBridgeImplementationType.class,
                            annotation.valueBridge().type(), annotation.valueBridge().name())
                    .orElseGet(() -> BeanReference.ofInstance((value, ctx) -> value));

            String fieldNamePrefix = annotation.name().isEmpty() ? context.annotatedElement().name() : annotation.name();

            // Create one field per language, populated from the relevant data in I18nData
            for (Language language : Language.values()) {
                mapping.keywordField(localizedName(fieldNamePrefix, language))
                        .valueBinder(new I18nDataBinder(language, valueBridgeRef))
                        .searchable(annotation.searchable())
                        .sortable(annotation.sortable())
                        .projectable(annotation.projectable())
                        .aggregable(annotation.aggregable())
                        .normalizer(localizedName(annotation.normalizerPrefix(), language));
            }
        }

    }

}
