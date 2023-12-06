package io.quarkus.search.app.hibernate;

import static io.quarkus.search.app.hibernate.I18nFullTextField.List;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hibernate.search.engine.backend.types.Highlightable;
import org.hibernate.search.engine.backend.types.TermVector;
import org.hibernate.search.engine.environment.bean.BeanRetrieval;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.ValueBridgeRef;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.PropertyMapping;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.PropertyMappingAnnotationProcessorRef;

@Documented
@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(List.class)
@PropertyMapping(processor = @PropertyMappingAnnotationProcessorRef(type = I18nFullTextFieldPropertyMappingAnnotationProcessor.class, retrieval = BeanRetrieval.CONSTRUCTOR))
public @interface I18nFullTextField {

    String name() default "";

    Highlightable[] highlightable() default { Highlightable.DEFAULT };

    TermVector termVector() default TermVector.DEFAULT;

    ValueBridgeRef valueBridge() default @ValueBridgeRef;

    String fallbackLanguage() default "en";

    Localization[] localization();

    @Documented
    @Target({ ElementType.METHOD, ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        I18nFullTextField[] value();
    }
}
