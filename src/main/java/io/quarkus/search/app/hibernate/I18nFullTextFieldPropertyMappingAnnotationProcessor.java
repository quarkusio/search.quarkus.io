package io.quarkus.search.app.hibernate;

import java.util.Arrays;
import java.util.Collections;

import org.hibernate.search.engine.backend.types.Highlightable;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.TermVector;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.ValueBridgeRef;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.PropertyMappingFieldOptionsStep;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.PropertyMappingFullTextFieldOptionsStep;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.PropertyMappingStep;

public class I18nFullTextFieldPropertyMappingAnnotationProcessor
        extends AbstractI18nTextFieldPropertyMappingAnnotationProcessor<I18nFullTextField, Localization> {
    @Override
    protected PropertyMappingFieldOptionsStep<?> initFieldMappingContext(Localization localization,
            PropertyMappingStep propertyMappingStep, I18nFullTextField annotation, String name) {
        PropertyMappingFullTextFieldOptionsStep step = propertyMappingStep
                .fullTextField("%s_%s".formatted(name, localization.language()))
                .projectable(Projectable.YES).analyzer(localization.analyzer());

        if (!localization.searchAnalyzer().isEmpty()) {
            step.searchAnalyzer(localization.searchAnalyzer());
        }

        TermVector termVector = annotation.termVector();
        if (!TermVector.DEFAULT.equals(termVector)) {
            step.termVector(termVector);
        }
        Highlightable[] highlightable = annotation.highlightable();
        if (!(highlightable.length == 1 && Highlightable.DEFAULT.equals(highlightable[0]))) {
            step.highlightable(
                    highlightable.length == 0 ? Collections.emptyList() : Arrays.asList(highlightable));
        }
        return step;
    }

    @Override
    protected Localization[] languages(I18nFullTextField annotation) {
        return annotation.localization();
    }

    @Override
    protected String stringRepresentation(Localization localization) {
        return localization.language();
    }

    @Override
    protected ValueBridgeRef valueBridge(I18nFullTextField annotation) {
        return annotation.valueBridge();
    }

    @Override
    protected String fallbackLanguage(I18nFullTextField annotation) {
        return annotation.fallbackLanguage();
    }

    @Override
    String name(I18nFullTextField annotation) {
        return annotation.name();
    }
}
