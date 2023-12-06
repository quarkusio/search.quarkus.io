package io.quarkus.search.app.entity;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Embeddable;

@Embeddable
public class I18nData<T> {
    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, T> data = new LinkedHashMap<>();

    public I18nData() {
    }

    public I18nData(T data) {
        add("en", data);
    }

    public I18nData<T> add(String language, T value) {
        data.put(language, value);
        return this;
    }

    public T get(String language) {
        return data.get(language);
    }

    public T get(String language, String fallbackLanguage) {
        T t = data.get(language);
        if (t == null) {
            return data.get(fallbackLanguage);
        }
        return t;
    }
}
