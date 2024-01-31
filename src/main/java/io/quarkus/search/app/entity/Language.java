package io.quarkus.search.app.entity;

import java.util.EnumSet;
import java.util.Set;

public enum Language {
    ENGLISH("en", "en_US"),
    SPANISH("es", "es_ES"),
    PORTUGUESE("pt", "pt_BR"),
    CHINESE("cn", "zh_CN"),
    JAPANESE("ja", "ja_JP");

    public static final Set<Language> NON_DEFAULT = EnumSet.of(Language.SPANISH, Language.PORTUGUESE, Language.CHINESE,
            Language.JAPANESE);

    public final String code;

    public final String locale;

    Language(String code, String locale) {
        this.code = code;
        this.locale = locale;
    }

    public String addSuffix(String prefix) {
        return prefix == null || prefix.isEmpty() ? null : "%s_%s".formatted(prefix, code);
    }

    @SuppressWarnings("unused")
    public static Language fromString(String value) {
        for (Language language : values()) {
            if (language.code.equalsIgnoreCase(value)) {
                return language;
            }
        }
        return null;
    }
}
