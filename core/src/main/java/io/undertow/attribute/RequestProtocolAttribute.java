package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The request protocol
 *
 * @author Stuart Douglas
 */
public class RequestProtocolAttribute implements ExchangeAttribute {

    public static final String REQUEST_PROTOCOL_SHORT = "%H";
    public static final String REQUEST_PROTOCOL = "%{PROTOCOL}";

    public static final ExchangeAttribute INSTANCE = new RequestProtocolAttribute();

    private RequestProtocolAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getProtocol().toString();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Request protocol", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Request protocol";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REQUEST_PROTOCOL) || token.equals(REQUEST_PROTOCOL_SHORT)) {
                return RequestProtocolAttribute.INSTANCE;
            }
            return null;
        }
    }
}
