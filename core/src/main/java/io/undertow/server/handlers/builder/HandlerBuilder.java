/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
