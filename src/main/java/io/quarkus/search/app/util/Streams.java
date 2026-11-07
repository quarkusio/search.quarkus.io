package io.quarkus.search.app.util;

import java.util.function.BinaryOperator;

public final class Streams {

    private Streams() {
    }

    public static <T> BinaryOperator<T> last() {
        return (first, second) -> second;
    }
}
