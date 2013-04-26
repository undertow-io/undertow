package io.undertow.predicate;

import io.undertow.server.HttpServerExchange;

/**
 * A predicate.
 *
 * This is mainly uses by handlers as a way to decide if a request should have certain
 * processing applied, based on the given conditions.
 *
 * @author Stuart Douglas
 */
public interface Predicate {

    boolean resolve(final HttpServerExchange value);

}
