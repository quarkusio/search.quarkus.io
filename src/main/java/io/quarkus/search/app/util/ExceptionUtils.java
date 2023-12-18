package io.quarkus.search.app.util;

import org.hibernate.search.util.common.AssertionFailure;

public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    public static RuntimeException toRuntimeException(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        } else if (throwable instanceof Error) {
            // Do not wrap errors: it would be "unreasonable" according to the Error javadoc
            throw (Error) throwable;
        } else if (throwable == null) {
            throw new AssertionFailure("Null throwable");
        } else {
            return new RuntimeException(throwable.getMessage(), throwable);
        }
    }

}
