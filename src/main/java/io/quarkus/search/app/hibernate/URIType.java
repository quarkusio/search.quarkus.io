package io.quarkus.search.app.hibernate;

import java.net.URI;

import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A Hibernate ORM {@link org.hibernate.type.descriptor.java.JavaType} for {@link URI}.
 * <p>
 * Weirdly, Hibernate ORM supports URL attributes but not URI attributes,
 * so by default it will handle them as serialized byte[], leading to strange errors.
 * <p>
 * To avoid that, we'll just do the conversion to String ourselves.
 * As we can't use {@link jakarta.persistence.AttributeConverter} on identifiers,
 * we have to implement the conversion as a {@link org.hibernate.type.descriptor.java.JavaType}.
 */
// Workaround for a problem similar to https://github.com/quarkusio/quarkus/issues/34071
@RegisterForReflection(targets = { URI[].class })
public class URIType extends AbstractClassJavaType<URI> {
    public URIType() {
        super(URI.class);
    }

    @Override
    public JdbcType getRecommendedJdbcType(JdbcTypeIndicators context) {
        return context.getJdbcType(SqlTypes.VARCHAR);
    }

    @Override
    public String toString(URI value) {
        return value.toString();
    }

    @Override
    public URI fromString(CharSequence string) {
        return URI.create(string.toString());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X> X unwrap(URI value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (URI.class.isAssignableFrom(type)) {
            return (X) value;
        }
        if (String.class.isAssignableFrom(type)) {
            return (X) toString(value);
        }
        throw unknownUnwrap(type);
    }

    @Override
    public <X> URI wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (value instanceof URI) {
            return (URI) value;
        }
        if (value instanceof CharSequence) {
            return fromString((CharSequence) value);
        }
        throw unknownWrap(value.getClass());
    }
}