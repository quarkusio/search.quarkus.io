package io.quarkus.search.app.util;

import org.hibernate.search.util.common.AssertionFailure;

public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    public static RuntimeException toRuntimeException(Throwable throwable) {
        if (throwable instanceof RuntimeException exception) {
            return exception;
        } else if (throwable instanceof Error error) {
            // Do not wrap errors: it would be "unreasonable" according to the Error javadoc
            throw error;
        } else if (throwable == null) {
            throw new AssertionFailure("Null throwable");
        } else {
            return new RuntimeException(throwable.getMessage(), throwable);
        }
    }

}
