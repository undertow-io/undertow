package io.undertow.testutils;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * @author Stuart Douglas
 */
@Retention(RUNTIME)
@Inherited
public @interface ProxyIgnore {
}
