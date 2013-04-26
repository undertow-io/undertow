package io.undertow.predicate;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
class OrPredicate implements Predicate {

    private final Predicate[] predicates;

    public OrPredicate(final Predicate... predicates) {
        this.predicates = predicates;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        for (final Predicate predicate : predicates) {
            if (predicate.resolve(value)) {
                return true;
            }
        }
        return false;
    }
}
