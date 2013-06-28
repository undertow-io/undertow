package io.undertow.attribute;

import java.net.InetSocketAddress;

import io.undertow.server.HttpServerExchange;

/**
 * The remote IP address
 *
 * @author Stuart Douglas
 */
public class RemoteIPAttribute implements ExchangeAttribute {

    public static final String REMOTE_IP_SHORT = "%a";
    public static final String REMOTE_HOST_NAME_SHORT = "%h";
    public static final String REMOTE_IP = "%{REMOTE_IP}";

    public static final ExchangeAttribute INSTANCE = new RemoteIPAttribute();

    private RemoteIPAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        final InetSocketAddress peerAddress = (InetSocketAddress) exchange.getConnection().getPeerAddress();
        return peerAddress.getAddress().getHostAddress();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("Remote IP", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Remote IP";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REMOTE_IP) || token.equals(REMOTE_IP_SHORT) || token.equals(REMOTE_HOST_NAME_SHORT)) {
                return RemoteIPAttribute.INSTANCE;
            }
            return null;
        }
    }
}
