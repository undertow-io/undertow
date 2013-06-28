package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * Exchange attribute that represents a fixed value
 *
 * @author Stuart Douglas
 */
public class ConstantExchangeAttribute implements ExchangeAttribute {

    private final String value;

    public ConstantExchangeAttribute(final String value) {
        this.value = value;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return value;
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("constant", newValue);
    }
}
