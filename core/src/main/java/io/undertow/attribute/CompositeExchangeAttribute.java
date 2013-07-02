package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * Exchange attribute that represents a combination of attributes that should be merged into a single string.
 *
 * @author Stuart Douglas
 */
public class CompositeExchangeAttribute implements ExchangeAttribute {

    private final ExchangeAttribute[] attributes;

    public CompositeExchangeAttribute(ExchangeAttribute[] attributes) {
        ExchangeAttribute[] copy = new ExchangeAttribute[attributes.length];
        System.arraycopy(attributes, 0, copy, 0, attributes.length);
        this.attributes = copy;
    }

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < attributes.length; ++i) {
            final String val = attributes[i].readAttribute(exchange);
            if(val != null) {
                sb.append(val);
            }
        }
        return sb.toString();
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("combined", newValue);
    }
}
