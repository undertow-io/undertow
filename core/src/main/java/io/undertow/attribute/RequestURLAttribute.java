package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.QueryParameterUtils;

/**
 * The request URL
 *
 * @author Stuart Douglas
 */
public class RequestURLAttribute implements ExchangeAttribute {

    public static final String REQUEST_URL_SHORT = "%U";
    public static final String REQUEST_URL = "%{REQUEST_URL}";

    public static final ExchangeAttribute INSTANCE = new RequestURLAttribute();

    private RequestURLAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getRequestURI();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        int pos = newValue.indexOf('?');
        if (pos == -1) {
            exchange.setRelativePath(newValue);
            exchange.setRequestURI(newValue);
            exchange.setRequestPath(newValue);
            exchange.setResolvedPath("");
        } else {
            final String path = newValue.substring(0, pos);
            exchange.setRelativePath(path);
            exchange.setRequestURI(path);
            exchange.setRequestPath(path);
            exchange.setResolvedPath("");
            final String newQueryString = newValue.substring(pos);
            exchange.setQueryString(newQueryString);
            exchange.getQueryParameters().putAll(QueryParameterUtils.parseQueryString(newQueryString.substring(1)));
        }

    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Request URL";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(REQUEST_URL) || token.equals(REQUEST_URL_SHORT)) {
                return RequestURLAttribute.INSTANCE;
            }
            return null;
        }
    }
}
