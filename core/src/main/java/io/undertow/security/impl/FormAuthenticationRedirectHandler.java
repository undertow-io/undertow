package io.undertow.security.impl;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;

/**
 * Handler that performs a redirect to the original location after a successful form auth
 *
 * @author Stuart Douglas
 */
public class FormAuthenticationRedirectHandler implements HttpHandler {

    private volatile HttpHandler next;

    public FormAuthenticationRedirectHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        final String location = exchange.getAttachment(FormAuthenticationMechanism.ORIGINAL_URL_LOCATION);
        if(location != null) {
            FormAuthenticationMechanism.sendRedirect(exchange, location);
            exchange.endExchange();
        } else {
            HttpHandlers.executeHandler(next, exchange);
        }
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }

}
