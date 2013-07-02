package io.undertow.attribute;

import java.util.Map;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
public class PredicateContextAttribute implements ExchangeAttribute {

    private final String name;

    public PredicateContextAttribute(final String name) {
        this.name = name;
    }

    @Override
    public String readAttribute(final HttpServerExchange exchange) {
        Map<String, Object> context = exchange.getAttachment(Predicate.PREDICATE_CONTEXT);
        if (context != null) {
            Object object = context.get(name);
            return object == null ? null : object.toString();
        }
        return null;
    }

    @Override
    public void writeAttribute(final HttpServerExchange exchange, final String newValue) throws ReadOnlyAttributeException {
        Map<String, Object> context = exchange.getAttachment(Predicate.PREDICATE_CONTEXT);
        if (context != null) {
            context.put(name, newValue);
        }
    }

    public static final class Builder implements ExchangeAttributeBuilder {

        @Override
        public String name() {
            return "Predicate context variable";
        }

        @Override
        public ExchangeAttribute build(final String token) {
            if (token.startsWith("${") && token.endsWith("}") && token.length() > 3) {
                return new PredicateContextAttribute(token.substring(2, token.length() - 1));
            } else if (token.startsWith("$")) {
                return new PredicateContextAttribute(token.substring(1, token.length()));
            }
            return null;
        }
    }
}
