package io.quarkus.search.app.hibernate;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Localization {

    String language();

    String analyzer() default "default";

    String searchAnalyzer() default "";
}
