package io.quarkus.search.app.hibernate;

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.bridge.mapping.annotation.ValueBridgeRef;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.PropertyMappingFieldOptionsStep;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.PropertyMappingKeywordFieldOptionsStep;
import org.hibernate.search.mapper.pojo.mapping.definition.programmatic.PropertyMappingStep;

public class I18nKeywordFieldPropertyMappingAnnotationProcessor
        extends AbstractI18nTextFieldPropertyMappingAnnotationProcessor<I18nKeywordField, String> {
    @Override
    protected PropertyMappingFieldOptionsStep<?> initFieldMappingContext(String language,
            PropertyMappingStep propertyMappingStep, I18nKeywordField annotation, String name) {
        PropertyMappingKeywordFieldOptionsStep step = propertyMappingStep.keywordField("%s_%s".formatted(name, language))
                .projectable(Projectable.YES);
        String normalizer = annotation.normalizer();
        if (!normalizer.isEmpty()) {
            step.normalizer(annotation.normalizer());
        }
        Sortable sortable = annotation.sortable();
        if (!Sortable.DEFAULT.equals(sortable)) {
            step.sortable(sortable);
        }

        Searchable searchable = annotation.searchable();
        if (!Searchable.DEFAULT.equals(searchable)) {
            step.searchable(searchable);
        }

        return step;
    }

    @Override
    protected String[] languages(I18nKeywordField annotation) {
        return annotation.languages();
    }

    @Override
    protected String stringRepresentation(String language) {
        return language;
    }

    @Override
    protected ValueBridgeRef valueBridge(I18nKeywordField annotation) {
        return annotation.valueBridge();
    }

    @Override
    protected String fallbackLanguage(I18nKeywordField annotation) {
        return annotation.fallbackLanguage();
    }

    @Override
    String name(I18nKeywordField annotation) {
        return annotation.name();
    }
}
