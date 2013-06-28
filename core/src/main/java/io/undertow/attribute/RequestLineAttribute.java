package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The request line
 *
 * @author Stuart Douglas
 */
public class RequestLineAttribute implements ExchangeAttribute {

    public static final String REQUEST_LINE_SHORT = "%r";
    public static final String REQUEST_LINE = "%{REQUEST_LINE}";

    public static final ExchangeAttribute INSTANCE = new RequestLineAttribute();

    private RequestLineAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return new StringBuilder()
                .append(exchange.getRequestMethod().toString())
                .append(' ')
                .append(exchange.getRequestURI())
                .append(' ')
                .append(exchange.getProtocol().toString()).toString();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Request line", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Request line";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REQUEST_LINE) || token.equals(REQUEST_LINE_SHORT)) {
                return RequestLineAttribute.INSTANCE;
            }
            return null;
        }
    }
}
