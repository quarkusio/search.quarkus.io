package io.quarkus.search.app.util;

import java.util.function.BinaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.kohsuke.github.PagedIterable;

public final class Streams {

    private Streams() {
    }

    public static <T> Stream<T> toStream(PagedIterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public static <T> BinaryOperator<T> last() {
        return (first, second) -> second;
    }
}
