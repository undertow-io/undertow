package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * A request header
 *
 * @author Stuart Douglas
 */
public class RequestHeaderAttribute implements ExchangeAttribute {


    private final HttpString requestHeader;

    public RequestHeaderAttribute(final HttpString requestHeader) {
        this.requestHeader = requestHeader;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getRequestHeaders().getFirst(requestHeader);
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        exchange.getRequestHeaders().put(requestHeader, newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Request header";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.startsWith("%{i,") && token.endsWith("}")) {
                final HttpString headerName = HttpString.tryFromString(token.substring(4, token.length() - 1));
                return new RequestHeaderAttribute(headerName);
            }
            return null;
        }
    }
}
