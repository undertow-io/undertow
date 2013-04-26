package io.undertow.predicate;

import io.undertow.server.HttpServerExchange;

/**
 * @author Stuart Douglas
 */
class FalsePredicate implements Predicate {

    public static final FalsePredicate INSTANCE = new FalsePredicate();

    public static FalsePredicate instance() {
        return INSTANCE;
    }

    @Override
    public boolean resolve(final HttpServerExchange value) {
        return false;
    }
}
