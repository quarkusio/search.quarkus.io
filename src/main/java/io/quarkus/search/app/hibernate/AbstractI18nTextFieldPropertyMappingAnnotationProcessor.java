package io.quarkus.search.app.hibernate;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;

import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.ValueBridgeRef;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeToIndexedValueContext;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.PropertyMappingAnnotationProcessor;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.processing.PropertyMappingAnnotationProcessorContext;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.PropertyMappingFieldOptionsStep;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.PropertyMappingStep;

import io.quarkus.search.app.entity.I18nData;

public abstract class AbstractI18nTextFieldPropertyMappingAnnotationProcessor<A extends Annotation, S>
        implements PropertyMappingAnnotationProcessor<A> {
    @Override
    public void process(PropertyMappingStep propertyMappingStep, A annotation,
            PropertyMappingAnnotationProcessorContext processorContext) {
        String name = name(annotation);
        if ("".equals(name)) {
            name = processorContext.annotatedElement().name();
        }

        String fallbackLanguage = fallbackLanguage(annotation);
        ValueBridgeRef valueBridgeRef = valueBridge(annotation);

        for (S language : languages(annotation)) {

            PropertyMappingFieldOptionsStep<?> fieldContext = initFieldMappingContext(language, propertyMappingStep, annotation,
                    name);

            ValueBridge delegate;
            if (ValueBridgeRef.UndefinedBridgeImplementationType.class.equals(valueBridgeRef.type())) {
                // no bridge so we just want a value
                delegate = (str, ctx) -> str;
            } else {
                delegate = bridgeDelegate(valueBridgeRef);
            }
            fieldContext.valueBridge(new I18nDataBridge(delegate, stringRepresentation(language), fallbackLanguage));
        }
    }

    protected abstract PropertyMappingFieldOptionsStep<?> initFieldMappingContext(S language,
            PropertyMappingStep propertyMappingStep, A annotation, String name);

    protected abstract S[] languages(A annotation);

    protected abstract String stringRepresentation(S language);

    protected abstract ValueBridgeRef valueBridge(A annotation);

    protected abstract String fallbackLanguage(A annotation);

    abstract String name(A annotation);

    private static ValueBridge bridgeDelegate(ValueBridgeRef valueBridgeRef) {
        try {
            return valueBridgeRef.type().getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static class I18nDataBridge implements ValueBridge<I18nData, String> {
        private final ValueBridge<Object, String> delegate;
        private final String language;
        private final String fallbackLanguage;

        private I18nDataBridge(ValueBridge<Object, String> delegate, String language, String fallbackLanguage) {
            this.delegate = delegate;
            this.language = language;
            this.fallbackLanguage = fallbackLanguage;
        }

        @Override
        public String toIndexedValue(I18nData data, ValueBridgeToIndexedValueContext ctx) {
            Object o = data.get(language, fallbackLanguage);
            return delegate.toIndexedValue(o, ctx);
        }
    }

}
