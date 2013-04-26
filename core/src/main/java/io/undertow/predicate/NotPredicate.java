package io.undertow.predicate;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
class NotPredicate implements Predicate {

    private final Predicate predicate;

    public NotPredicate(final Predicate predicate) {
        this.predicate = predicate;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        return !predicate.resolve(value);
    }
}
