package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;

/**
 * @author Stuart Douglas
 */
public class SslCipherAttribute implements ExchangeAttribute {

    public static final SslCipherAttribute INSTANCE = new SslCipherAttribute();

    @Override
    public String readAttribute(HttpServerExchange exchange) {
        SSLSessionInfo ssl = exchange.getConnection().getSslSessionInfo();
        if(ssl == null) {
            return null;
        }
        return ssl.getCipherSuite();
    }

    @Override
    public void writeAttribute(HttpServerExchange exchange, String newValue) throws ReadOnlyAttributeException {
        throw new ReadOnlyAttributeException("SSL Cipher", newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "SSL Cipher";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals("%{SSL_CIPHER}")) {
                return INSTANCE;
            }
            return null;
        }
    }
}
