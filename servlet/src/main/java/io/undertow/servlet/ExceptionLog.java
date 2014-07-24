package io.undertow.servlet;

import org.jboss.logging.Logger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that can be applied to exceptions to control how they are logged by Undertow.
 *
 * Note that this will only take effect if the deployments error handler has not been changed.
 *
 * @author Stuart Douglas
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface ExceptionLog {
    /**
     * The default log level for this exception.
     */
    Logger.Level value() default Logger.Level.ERROR;

    /**
     * The level at which to log stack traces. If this is a higher level
     * than the default then they will be logged by default at the default level.
     */
    Logger.Level stackTraceLevel() default Logger.Level.FATAL;

    /**
     * The category to log this exception under
     */
    String category();

}
