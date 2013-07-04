package io.undertow.server.handlers.builder;

import io.undertow.predicate.Predicate;
import io.undertow.server.HandlerWrapper;

/**
 * @author Stuart Douglas
 */
public class PredicatedHandler {
    private final Predicate predicate;
    private final HandlerWrapper handler;

    public PredicatedHandler(Predicate predicate, HandlerWrapper handler) {
        this.predicate = predicate;
        this.handler = handler;
    }

    public Predicate getPredicate() {
        return predicate;
    }

    public HandlerWrapper getHandler() {
        return handler;
    }
}
