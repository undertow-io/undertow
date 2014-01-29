package io.undertow.predicate;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.PathMatcher;

/**
 * @author Stuart Douglas
 */
class PathMatchPredicate implements Predicate {

    private final PathMatcher<Boolean> pathMatcher;

    public PathMatchPredicate(final String... paths) {
        PathMatcher<Boolean> matcher = new PathMatcher<Boolean>();
        for(String path : paths) {
            if(!path.startsWith("/")) {
                matcher.addExactPath("/" + path, Boolean.TRUE);
            } else {
                matcher.addExactPath(path, Boolean.TRUE);
            }
        }
        this.pathMatcher = matcher;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        final String relativePath = value.getRelativePath();
        PathMatcher.PathMatch<Boolean> result = pathMatcher.match(relativePath);
        return result.getValue() == Boolean.TRUE;
    }

    public static class Builder implements PredicateBuilder {

        @Override
        public String name() {
            return "path";
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
            return new PathMatchPredicate(path);
        }
    }
}
