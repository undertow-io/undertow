package io.undertow.predicate;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
class PrefixMatchPredicate implements Predicate {

    private final String slashPath;
    private final String path;

    public PrefixMatchPredicate(final String path) {
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
}
