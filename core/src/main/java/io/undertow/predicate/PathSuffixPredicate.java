package io.undertow.predicate;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
class PathSuffixPredicate implements Predicate {

    private final String suffix;

    public PathSuffixPredicate(final String suffix) {
            this.suffix = suffix;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        return value.getRelativePath().endsWith(suffix);
    }


    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "path-suffix";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("path", String[].class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("path");
        }

        @Override
        public String defaultParameter() {
            return "path";
        }

        @Override
        public Predicate build(final Map<String, Object> config) {
            String[] path = (String[]) config.get("path");
            return Predicates.suffixes(path);
        }
    }
}
