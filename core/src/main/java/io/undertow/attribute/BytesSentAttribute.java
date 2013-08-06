package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The bytes sent
 *
 * @author Filipe Ferraz
 */
public class BytesSentAttribute implements ExchangeAttribute {

    public static final String BYTES_SENT_SHORT_UPPER = "%B";
    public static final String BYTES_SENT_SHORT_LOWER = "%b";
    public static final String BYTES_SENT = "%{BYTES_SENT}";

    private final String attribute;

    public BytesSentAttribute(final String attribute) {
        this.attribute = attribute;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        if (attribute.equals(BYTES_SENT_SHORT_LOWER))  {
            long bytesSent = exchange.getResponseContentLength();
            return bytesSent == 0 ? "-" : Long.toString(bytesSent);
        } else {
            return Long.toString(exchange.getResponseContentLength());
        }
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
            if (token.equals(BYTES_SENT) || token.equals(BYTES_SENT_SHORT_UPPER) || token.equals(BYTES_SENT_SHORT_LOWER)) {
                return new BytesSentAttribute(token);
            }
            return null;
        }
    }
}
