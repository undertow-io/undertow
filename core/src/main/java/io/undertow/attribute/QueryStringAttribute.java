package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

/**
 * The query string
 *
 * @author Stuart Douglas
 */
public class QueryStringAttribute implements ExchangeAttribute {

    public static final String QUERY_STRING_SHORT = "%q";
    public static final String QUERY_STRING = "%{QUERY_STRING}";

    public static final ExchangeAttribute INSTANCE = new QueryStringAttribute();

    private QueryStringAttribute() {

    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        return exchange.getQueryString();
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        exchange.setQueryString(newValue);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Query String";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.equals(QUERY_STRING) || token.equals(QUERY_STRING_SHORT)) {
                return QueryStringAttribute.INSTANCE;
            }
            return null;
        }
    }
}
