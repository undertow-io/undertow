package io.undertow.server.handlers.builder;

import io.undertow.server.HandlerWrapper;
import java.util.Map;
import java.util.Set;

/**
 * Interface that provides a way of providing a textual representation of a handler.
 *
 * @author Stuart Douglas
 */
public interface HandlerBuilder {

    /**
     * The string representation of the handler name.
     *
     * @return The handler name
     */
    String name();

    /**
     * Returns a map of parameters and their types.
     */
    Map<String, Class<?>> parameters();

    /**
     * @return The required parameters
     */
    Set<String> requiredParameters();

    /**
     * @return The default parameter name, or null if it does not have a default parameter
     */
    String defaultParameter();

    /**
     * Creates the handler
     *
     * @param config The handler config
     * @return The new predicate
     */
    HandlerWrapper build(final Map<String, Object> config);


}
