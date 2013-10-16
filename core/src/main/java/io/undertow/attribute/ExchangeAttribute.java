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
     */
    void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException;
}
