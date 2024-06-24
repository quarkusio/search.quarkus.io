package io.quarkus.search.app.entity;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.hibernate.Length;

@Embeddable
public class I18nData<T> {
    @Column(length = Length.LONG32)
    private T en;
    @Column(length = Length.LONG32)
    private T es;
    @Column(length = Length.LONG32)
    private T pt;
    @Column(length = Length.LONG32)
    private T cn;
    @Column(length = Length.LONG32)
    private T ja;

    public I18nData() {
    }

    public I18nData(Language language, T value) {
        set(language, value);
    }

    @Override
    public String toString() {
        return "I18nData" + asMap();
    }

    public void set(Language language, T value) {
        switch (language) {
            case ENGLISH -> en = value;
            case SPANISH -> es = value;
            case PORTUGUESE -> pt = value;
            case CHINESE -> cn = value;
            case JAPANESE -> ja = value;
        }
    }

    public void set(T value) {
        en = value;
        es = value;
        pt = value;
        cn = value;
        ja = value;
    }

    public T get(Language language) {
        return switch (language) {
            case ENGLISH -> en;
            case SPANISH -> es;
            case PORTUGUESE -> pt;
            case CHINESE -> cn;
            case JAPANESE -> ja;
        };
    }

    // For testing
    public Map<Language, T> asMap() {
        var result = new LinkedHashMap<Language, T>();
        for (var language : Language.values()) {
            var value = get(language);
            if (value != null) {
                result.put(language, value);
            }
        }
        return result;
    }
}
