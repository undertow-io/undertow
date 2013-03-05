package io.undertow.predicate;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
class SuffixMatchPredicate implements Predicate<HttpServerExchange> {

    private final String suffix;

    public SuffixMatchPredicate(final String suffix) {
            this.suffix = suffix;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        return value.getCanonicalPath().endsWith(suffix);
    }
}
