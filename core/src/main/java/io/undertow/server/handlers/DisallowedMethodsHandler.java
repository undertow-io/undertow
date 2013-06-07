package io.undertow.server.handlers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

/**
 * Handler that blacklists certain HTTP methods.
 *
 * @author Stuart Douglas
 */
public class DisallowedMethodsHandler implements HttpHandler {

    private final Set<HttpString> disallowedMethods;
    private final HttpHandler next;

    public DisallowedMethodsHandler(final HttpHandler next, final Set<HttpString> disallowedMethods) {
        this.disallowedMethods = new HashSet<HttpString>(disallowedMethods);
        this.next = next;
    }


    public DisallowedMethodsHandler(final HttpHandler next, final HttpString... disallowedMethods) {
        this.disallowedMethods = new HashSet<HttpString>(Arrays.asList(disallowedMethods));
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (disallowedMethods.contains(exchange.getRequestMethod())) {
            exchange.setResponseCode(StatusCodes.METHOD_NOT_ALLOWED);
            exchange.endExchange();
        } else {
            next.handleRequest(exchange);
        }
    }

}
