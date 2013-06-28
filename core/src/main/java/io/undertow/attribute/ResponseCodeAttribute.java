package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The request status code
 *
 * @author Stuart Douglas
 */
public class ResponseCodeAttribute implements ExchangeAttribute {

    public static final String RESPONSE_CODE_SHORT = "%s";
    public static final String RESPONSE_CODE = "%{RESPONSE_CODE}";

    public static final ExchangeAttribute INSTANCE = new ResponseCodeAttribute();

    private ResponseCodeAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return Integer.toString(exchange.getResponseCode());
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        exchange.setResponseCode(Integer.parseInt(newValue));
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Response code";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(RESPONSE_CODE) || token.equals(RESPONSE_CODE_SHORT)) {
                return ResponseCodeAttribute.INSTANCE;
            }
            return null;
        }
    }
}
