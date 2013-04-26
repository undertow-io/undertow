package io.undertow.predicate;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
class TruePredicate implements Predicate {

    public static final TruePredicate INSTANCE = new TruePredicate();

    public static TruePredicate instance() {
        return INSTANCE;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        return true;
    }
}
