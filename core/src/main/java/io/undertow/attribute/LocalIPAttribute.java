package io.undertow.attribute;

import java.net.InetSocketAddress;

import io.undertow.server.HttpServerExchange;

/**
 * The local IP address
 *
 * @author Stuart Douglas
 */
public class LocalIPAttribute implements ExchangeAttribute {

    public static final String LOCAL_IP = "%{LOCAL_IP}";
    public static final String LOCAL_IP_SHORT = "%A";

    public static final ExchangeAttribute INSTANCE = new LocalIPAttribute();

    private LocalIPAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        InetSocketAddress localAddress = (InetSocketAddress) exchange.getConnection().getLocalAddress();
        return localAddress.getAddress().getHostAddress();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Local IP", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Local IP";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(LOCAL_IP) || token.equals(LOCAL_IP_SHORT)) {
                return LocalIPAttribute.INSTANCE;
            }
            return null;
        }
    }
}
