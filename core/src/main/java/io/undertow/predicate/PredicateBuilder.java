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

package io.undertow.predicate;

import java.util.Map;
import java.util.Set;

/**
 * An interface that knows how to build a predicate from a textual representation. This is loaded
 * using a service loader to make it configurable.
 * <p>
 * This makes it easy to configure conditions based on a string representation
 *
 * @author Stuart Douglas
 */
public interface PredicateBuilder {

    /**
     * The string representation of the predicate name.
     *
     * @return The predicate name
     */
    String name();

    /**
     * Returns a map of parameters and their types.
     */
    Map<String, Class<?>> parameters();

    /**
     *
     * @return The required parameters
     */
    Set<String> requiredParameters();

    /**
     * @return The default parameter name, or null if it does not have a default parameter
     */
    String defaultParameter();

    /**
     * Creates a predicate
     *
     * @param config The predicate config
     * @return The new predicate
     */
    Predicate build(final Map<String, Object> config);

}
