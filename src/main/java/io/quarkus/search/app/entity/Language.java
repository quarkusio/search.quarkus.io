package io.quarkus.search.app.entity;

import java.util.EnumSet;
import java.util.Set;

public enum Language {
    ENGLISH("en"),
    SPANISH("es"),
    PORTUGUESE("pt"),
    CHINESE("cn"),
    JAPANESE("ja");

    public static final Set<Language> nonDefault = EnumSet.of(Language.SPANISH, Language.PORTUGUESE, Language.CHINESE,
            Language.JAPANESE);

    public final String code;

    Language(String code) {
        this.code = code;
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
