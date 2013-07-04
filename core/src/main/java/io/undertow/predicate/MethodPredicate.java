package io.undertow.predicate;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
class MethodPredicate implements Predicate {

    private final HttpString[] methods;

    MethodPredicate(String[] methods) {
        HttpString[] values = new HttpString[methods.length];
        for(int i = 0; i < methods.length; ++i) {
            values[i] = HttpString.tryFromString(methods[i]);
        }
        this.methods = values;
    }


    @Override
    public boolean resolve(final HttpServerExchange value) {
        for(int i =0; i < methods.length; ++i) {
            if(value.getRequestMethod().equals(methods[i])) {
                return true;
            }
        }
        return false;
    }


    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "method";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("value", String[].class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("value");
        }

        @Override
        public String defaultParameter() {
            return "value";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            String[] methods = (String[]) config.get("value");
            return new MethodPredicate(methods);
        }
    }
}
