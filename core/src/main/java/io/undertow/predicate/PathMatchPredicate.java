package io.undertow.predicate;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
public class PathMatchPredicate implements Predicate<HttpServerExchange> {

    private final String path;

    public PathMatchPredicate(final String path) {
        this.path = path;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        return value.getRelativePath().equals(path);
    }
}
