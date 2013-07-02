package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * A response header
 *
 * @author Stuart Douglas
 */
public class ResponseHeaderAttribute implements ExchangeAttribute {


    private final HttpString responseHeader;

    public ResponseHeaderAttribute(final HttpString responseHeader) {
        this.responseHeader = responseHeader;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getResponseHeaders().getFirst(responseHeader);
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        exchange.getResponseHeaders().put(responseHeader, newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Response header";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.startsWith("%{o,") && token.endsWith("}")) {
                final HttpString headerName = HttpString.tryFromString(token.substring(4, token.length() - 1));
                return new ResponseHeaderAttribute(headerName);
            }
            return null;
        }
    }
}
