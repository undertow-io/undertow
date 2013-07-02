package io.undertow.attribute;

import io.undertow.server.HttpServerExchange;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Query parameter
 *
 * @author Stuart Douglas
 */
public class QueryParameterAttribute implements ExchangeAttribute {


    private final String parameter;

    public QueryParameterAttribute(String parameter) {
        this.parameter = parameter;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        Deque<String> res = exchange.getQueryParameters().get(parameter);
        if(res == null) {
            return null;
        }else if(res.isEmpty()) {
            return "";
        } else if(res.size() ==1) {
            return res.getFirst();
        } else {
            StringBuilder sb = new StringBuilder("[");
            int i = 0;
            for(String s : res) {
                sb.append(s);
                if(++i != res.size()) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        final ArrayDeque<String> value = new ArrayDeque<String>();
        value.add(newValue);
        exchange.getQueryParameters().put(parameter, value);
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Query Parameter";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.startsWith("%{q,") && token.endsWith("}")) {
                final String qp = token.substring(4, token.length() - 1);
                return new QueryParameterAttribute(qp);
            }
            return null;
        }
    }
}
