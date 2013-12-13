package io.undertow.testutils;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Stuart Douglas
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface AjpIgnore {
    boolean apacheOnly() default false;

    String value() default "";
}
