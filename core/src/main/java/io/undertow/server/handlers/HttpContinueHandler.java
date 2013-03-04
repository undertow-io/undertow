package io.undertow.server.handlers;

import java.io.IOException;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpContinue;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Handler that provides support for HTTP/1.1 continue responses.
 * <p/>
 * By default this will accept all requests. To change this behaviour this
 * handler must be subclassed and the {@link #acceptRequest(io.undertow.server.HttpServerExchange)}
 * method overridden tp provide the desired behaviour.
 *
 * @see io.undertow.server.HttpContinue
 * @author Stuart Douglas
 */
public class HttpContinueHandler implements HttpHandler {

    private volatile HttpHandler next;

    public HttpContinueHandler(HttpHandler next) {
        this.next = next;
    }

    public HttpContinueHandler() {
        this(ResponseCodeHandler.HANDLE_404);
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        if(HttpContinue.requiresContinueResponse(exchange)) {
            if(acceptRequest(exchange)) {
                HttpContinue.sendContinueResponse(exchange, new IoCallback() {
                    @Override
                    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                        HttpHandlers.executeHandler(next, exchange);
                    }

                    @Override
                    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                        UndertowLogger.REQUEST_LOGGER.debugf("IOException writing HTTP/1.1 100 Continue response");
                        exchange.endExchange();
                    }
                });

            } else {
                HttpContinue.rejectExchange(exchange);
            }
        } else {
            HttpHandlers.executeHandler(next, exchange);
        }
    }

    protected boolean acceptRequest(final HttpServerExchange exchange) {
        return true;
    }

    public HttpHandler getNext() {
        return next;
    }

    public HttpContinueHandler setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
        return this;
    }
}
