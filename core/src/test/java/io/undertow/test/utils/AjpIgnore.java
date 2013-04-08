package io.undertow.test.utils;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Stuart Douglas
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface AjpIgnore {
}
