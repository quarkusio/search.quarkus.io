package io.quarkus.search.app.hibernate;

import java.nio.file.Path;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DefaultFilesystemPathConverter implements AttributeConverter<PathWrapper, String> {
    @Override
    public String convertToDatabaseColumn(PathWrapper attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public PathWrapper convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new PathWrapper(Path.of(dbData));
    }
}
