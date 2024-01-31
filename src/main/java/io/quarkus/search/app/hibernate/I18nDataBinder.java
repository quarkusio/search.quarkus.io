package io.quarkus.search.app.hibernate;

import io.quarkus.search.app.entity.I18nData;
import io.quarkus.search.app.entity.Language;

import org.hibernate.search.engine.environment.bean.BeanHolder;
import org.hibernate.search.engine.environment.bean.BeanReference;
import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.ValueBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.ValueBinder;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeToIndexedValueContext;

public class I18nDataBinder implements ValueBinder {

    private final BeanReference<? extends ValueBridge<?, String>> valueBridgeRef;
    private final Language language;

    public I18nDataBinder(Language language, BeanReference<? extends ValueBridge<?, String>> valueBridgeRef) {
        this.language = language;
        this.valueBridgeRef = valueBridgeRef;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" }) // Unfortunately this API can't handle generics at the moment
    @Override
    public void bind(ValueBindingContext<?> context) {
        var delegateHolder = valueBridgeRef.resolve(context.beanResolver());
        context.bridge(I18nData.class,
                (BeanHolder) BeanHolder.of(new Bridge<>(language, delegateHolder.get()))
                        .withDependencyAutoClosing(delegateHolder),
                null);
    }

    static class Bridge<V> implements ValueBridge<I18nData<V>, String> {
        private final Language language;
        private final ValueBridge<V, String> delegate;

        private Bridge(Language language, ValueBridge<V, String> delegate) {
            this.language = language;
            this.delegate = delegate;
        }

        @Override
        public String toIndexedValue(I18nData<V> value, ValueBridgeToIndexedValueContext context) {
            if (value == null) {
                return null;
            }
            var localized = value.get(language);
            if (localized == null) {
                return null;
            }
            return delegate.toIndexedValue(localized, context);
        }
    }
}
