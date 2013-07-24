package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The bytes sent modified
 *
 * @author Filipe Ferraz
 */
public class BytesSentModifiedAttribute implements ExchangeAttribute {

    public static final String BYTES_SENT_SHORT = "%b";
    public static final String BYTES_SENT = "%{BYTES_SENT}";

    public static final ExchangeAttribute INSTANCE = new BytesSentModifiedAttribute();

    private BytesSentModifiedAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        long bytesSent = exchange.getResponseContentLength();
        return bytesSent == 0 ? "-" : Long.toString(bytesSent);
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
                return BytesSentModifiedAttribute.INSTANCE;
            }
            return null;
        }
    }
}
