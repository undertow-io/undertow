package io.undertow.server.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * A handler for redirects.
 * <p/>
 *
 * @author Stuart Douglas
 */
public class RedirectHandler implements HttpHandler {

    private final String location;

    public RedirectHandler(final String location) {
        this.location = location;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.setResponseCode(302);
        exchange.getResponseHeaders().put(Headers.LOCATION, location);
        exchange.endExchange();
    }

}
