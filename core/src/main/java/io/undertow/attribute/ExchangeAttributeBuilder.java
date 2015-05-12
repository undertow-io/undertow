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

package io.undertow.attribute;

/**
 * An interface that knows how to build an exchange attribute from a textual representation.
 * <p>
 * This makes it easy to configure attributes based on a string representation
 *
 * @author Stuart Douglas
 */
public interface ExchangeAttributeBuilder {

    /**
     * The string representation of the attribute name. This is used solely for debugging / informational purposes
     *
     * @return The attribute name
     */
    String name();

    /**
     * Build the attribute from a text based representation. If the attribute does not understand this representation then
     * it will just return null.
     *
     * @param token The string token
     * @return The exchange attribute, or null
     */
    ExchangeAttribute build(final String token);

    /**
     * The priority of the builder. Builders will be tried in priority builder. Built in builders use the priority range 0-100,
     *
     * @return The priority
     */
    int priority();

}
