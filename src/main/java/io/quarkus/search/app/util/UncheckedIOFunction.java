package io.quarkus.search.app.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

@FunctionalInterface
public interface UncheckedIOFunction<T, R> extends Function<T, R> {
    static <T, R> Function<T, R> uncheckedIO(UncheckedIOFunction<T, R> uncheckedIoFunction) {
        return uncheckedIoFunction;
    }

    @Override
    default R apply(T t) {
        try {
            return applyIO(t);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    R applyIO(T t) throws IOException;
}
