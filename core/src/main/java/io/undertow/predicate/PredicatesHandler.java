package io.undertow.predicate;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.PredicatedHandler;
import io.undertow.util.AttachmentKey;

import java.util.TreeMap;

/**
 * Handler that can deal with a large number of predicates. chaining together a large number of {@link io.undertow.predicate.PredicatesHandler.Holder}
 * instances will make the stack grow to large, so this class is used that can deal with a large number of predicates.
 *
 * @author Stuart Douglas
 */
public class PredicatesHandler implements HttpHandler {

    private volatile Holder[] handlers = new Holder[0];
    private final HttpHandler next;

    //non-static, so multiple handlers can co-exist
    private final AttachmentKey<Integer> CURRENT_POSITION = AttachmentKey.create(Integer.class);

    public PredicatesHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final int length = handlers.length;
        Integer current = exchange.getAttachment(CURRENT_POSITION);
        int pos;
        if (current == null) {
            pos = 0;
            exchange.putAttachment(Predicate.PREDICATE_CONTEXT, new TreeMap<String, Object>());
        } else {
            pos = current;
        }
        for (; pos < length; ++pos) {
            final Holder handler = handlers[pos];
            if (handler.predicate.resolve(exchange)) {
                exchange.putAttachment(CURRENT_POSITION, pos + 1);
                handler.handler.handleRequest(exchange);
                return;
            }
        }
        next.handleRequest(exchange);

    }

    /**
     * Adds a new predicated handler.
     * <p/>
     *
     * @param predicate
     * @param handlerWrapper
     */
    public PredicatesHandler addPredicatedHandler(final Predicate predicate, final HandlerWrapper handlerWrapper) {
        Holder[] old = handlers;
        Holder[] handlers = new Holder[old.length + 1];
        System.arraycopy(old, 0, handlers, 0, old.length);
        handlers[old.length] = new Holder(predicate, handlerWrapper.wrap(this));
        this.handlers = handlers;
        return this;
    }

    public PredicatesHandler addPredicatedHandler(final PredicatedHandler handler) {
        return addPredicatedHandler(handler.getPredicate(), handler.getHandler());
    }

    private static final class Holder {
        final Predicate predicate;
        final HttpHandler handler;

        private Holder(Predicate predicate, HttpHandler handler) {
            this.predicate = predicate;
            this.handler = handler;
        }
    }
}
