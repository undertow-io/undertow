package io.undertow.server.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * A handler for redirects.
 * <p/>
 * TODO: this is pretty basic at the moment, it should support much more advanced rules
 *
 * @author Stuart Douglas
 */
public class RedirectHandler implements HttpHandler {

    private volatile String location;

    public RedirectHandler(final String location) {
        this.location = location;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        exchange.setResponseCode(302);
        exchange.getResponseHeaders().put(Headers.LOCATION, location);
        exchange.endExchange();
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(final String location) {
        this.location = location;
    }
}
