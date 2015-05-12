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

import io.undertow.server.HttpServerExchange;

/**
 * Representation of a string attribute from a HTTP server exchange.
 *
 *
 * @author Stuart Douglas
 */
public interface ExchangeAttribute {

    /**
     * Resolve the attribute from the HTTP server exchange. This may return null if the attribute is not present.
     * @param exchange The exchange
     * @return The attribute
     */
    String readAttribute(final HttpServerExchange exchange);

    /**
     * Sets a new value for the attribute. Not all attributes are writable.
     * @param exchange The exchange
     * @param newValue The new value for the attribute
     * @throws  ReadOnlyAttributeException when attribute cannot be written
     */
    void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException;
}
