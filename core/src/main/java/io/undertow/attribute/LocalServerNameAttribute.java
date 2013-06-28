package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * The local server name
 *
 * @author Stuart Douglas
 */
public class LocalServerNameAttribute implements ExchangeAttribute {

    public static final String LOCAL_SERVER_NAME_SHORT = "%v";
    public static final String LOCAL_SERVER_NAME = "%{LOCAL_SERVER_NAME}";

    public static final ExchangeAttribute INSTANCE = new LocalServerNameAttribute();

    private LocalServerNameAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getRequestHeaders().getFirst(Headers.HOST);
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Local server name", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Local server name";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(LOCAL_SERVER_NAME) || token.equals(LOCAL_SERVER_NAME_SHORT)) {
                return LocalServerNameAttribute.INSTANCE;
            }
            return null;
        }
    }
}
