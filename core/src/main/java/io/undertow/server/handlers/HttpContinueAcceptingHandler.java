package io.undertow.server.handlers;

import java.io.IOException;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.protocol.http.HttpContinue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Handler that provides support for HTTP/1.1 continue responses.
 * <p/>
 * If the provided predicate returns <code>true</code> then the request will be
 * accepted, otherwise it will be rejected.
 *
 * If no predicate is supplied then all requests will be accepted.
 *
 * @see io.undertow.server.protocol.http.HttpContinue
 * @author Stuart Douglas
 */
public class HttpContinueAcceptingHandler implements HttpHandler {

    private final HttpHandler next;
    private final Predicate accept;

    public HttpContinueAcceptingHandler(HttpHandler next, Predicate accept) {
        this.next = next;
        this.accept = accept;
    }

    public HttpContinueAcceptingHandler(HttpHandler next) {
        this(next, Predicates.truePredicate());
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if(HttpContinue.requiresContinueResponse(exchange)) {
            if(accept.resolve(exchange)) {
                HttpContinue.sendContinueResponse(exchange, new IoCallback() {
                    @Override
                    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                        exchange.dispatch(next);
                    }

                    @Override
                    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
                        exchange.endExchange();
                    }
                });

            } else {
                HttpContinue.rejectExchange(exchange);
            }
        } else {
            next.handleRequest(exchange);
        }
    }
}
