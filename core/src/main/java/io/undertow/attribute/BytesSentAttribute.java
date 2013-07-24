package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The bytes sent
 *
 * @author Filipe Ferraz
 */
public class BytesSentAttribute implements ExchangeAttribute {

    public static final String BYTES_SENT_SHORT = "%B";
    public static final String BYTES_SENT = "%{BYTES_SENT}";

    public static final ExchangeAttribute INSTANCE = new BytesSentAttribute();

    private BytesSentAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return Long.toString(exchange.getResponseContentLength());
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Bytes sent", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Bytes Sent";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(BYTES_SENT) || token.equals(BYTES_SENT_SHORT)) {
                return BytesSentAttribute.INSTANCE;
            }
            return null;
        }
    }
}
