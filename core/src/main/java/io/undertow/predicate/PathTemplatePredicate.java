package io.undertow.predicate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.PathTemplate;

/**
 * @author Stuart Douglas
 */
public class PathTemplatePredicate implements Predicate {

    private final ExchangeAttribute attribute;
    private final PathTemplate value;

    public PathTemplatePredicate(final String template, final ExchangeAttribute attribute) {
        this.attribute = attribute;
        this.value = PathTemplate.create(template);
    }

    @Override
    public boolean resolve(final HttpServerExchange exchange) {
        final Map<String, String> params = new HashMap<String, String>();
        boolean result = this.value.matches(attribute.readAttribute(exchange), params);
        if (result) {
            Map<String, Object> context = exchange.getAttachment(PREDICATE_CONTEXT);
            if (context != null) {
                context.putAll(params);
            }
        }
        return result;
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "path-template";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            final Map<String, Class<?>> params = new HashMap<String, Class<?>>();
            params.put("value", String.class);
            params.put("match", ExchangeAttribute.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            final Set<String> params = new HashSet<String>();
            params.add("value");
            return params;
        }

        @Override
        public String defaultParameter() {
            return "value";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            ExchangeAttribute match = (ExchangeAttribute) config.get("match");
            if (match == null) {
                match = ExchangeAttributes.relativePath();
            }
            String value = (String) config.get("value");
            return new PathTemplatePredicate(value, match);
        }
    }

}
