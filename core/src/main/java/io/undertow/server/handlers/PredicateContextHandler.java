package io.undertow.server.handlers;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.util.HashMap;

/**
 * Handler that sets up the predicate context
 *
 * @author Stuart Douglas
 */
public class PredicateContextHandler implements HttpHandler {

    private final HttpHandler next;

    public PredicateContextHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.putAttachment(Predicate.PREDICATE_CONTEXT, new HashMap<String, Object>());
        next.handleRequest(exchange);
    }
}
