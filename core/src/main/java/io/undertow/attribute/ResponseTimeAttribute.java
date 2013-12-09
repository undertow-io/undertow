package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The response time
 *
 * This will only work if {@link io.undertow.UndertowOptions#RECORD_REQUEST_START_TIME} has been set
 */
public class ResponseTimeAttribute implements ExchangeAttribute {

    public static final String RESPONSE_TIME_SHORT = "%D";
    public static final String RESPONSE_TIME = "%{RESPONSE_TIME}";

    public static final ExchangeAttribute INSTANCE = new ResponseTimeAttribute();

    private ResponseTimeAttribute() {}

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        long requestStartTime = exchange.getRequestStartTime();
        if(requestStartTime == -1) {
            return null;
        }
        return String.valueOf(System.nanoTime() - requestStartTime);
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Response Time", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Response Time";
        }

        @Override
        public ExchangeAttribute build(String token) {
            if (token.equals(RESPONSE_TIME) || token.equals(RESPONSE_TIME_SHORT)) {
                return ResponseTimeAttribute.INSTANCE;
            }
            return null;
        }
    }

}
