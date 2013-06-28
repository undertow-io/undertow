package io.undertow.predicate;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
class PathPrefixPredicate implements Predicate {

    private final String slashPath;
    private final String path;

    public PathPrefixPredicate(final String path) {
        if (path.startsWith("/")) {
            this.slashPath = path;
            this.path = path.substring(1);
        } else {
            this.slashPath = "/" + path;
            this.path = path;
        }
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        final String relativePath = value.getRelativePath();
        if (relativePath.startsWith("/")) {
            return relativePath.startsWith(slashPath);
        } else {
            return relativePath.startsWith(path);
        }
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "path-prefix";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("path", String.class);
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
            String path = (String) config.get("path");
            return new PathPrefixPredicate(path);
        }
    }
}
