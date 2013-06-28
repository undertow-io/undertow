package io.undertow.attribute;

import java.net.InetSocketAddress;

import io.undertow.server.HttpServerExchange;

/**
 * The local port
 *
 * @author Stuart Douglas
 */
public class LocalPortAttribute implements ExchangeAttribute {

    public static final String LOCAL_PORT_SHORT = "%p";
    public static final String LOCAL_PORT = "%{LOCAL_PORT}";

    public static final ExchangeAttribute INSTANCE = new LocalPortAttribute();

    private LocalPortAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        InetSocketAddress localAddress = (InetSocketAddress) exchange.getConnection().getLocalAddress();
        return Integer.toString(localAddress.getPort());
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Local port", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Local Port";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(LOCAL_PORT) || token.equals(LOCAL_PORT_SHORT)) {
                return LocalPortAttribute.INSTANCE;
            }
            return null;
        }
    }
}
