package io.undertow.server.handlers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

/**
 * Handler that whitelists certain HTTP methods. Only requests with a method in
 * the allowed methods set will be allowed to continue.
 *
 * @author Stuart Douglas
 */
public class AllowedMethodsHandler implements HttpHandler {

    private final Set<HttpString> allowedMethods;
    private final HttpHandler next;

    public AllowedMethodsHandler(final HttpHandler next, final Set<HttpString> allowedMethods) {
        this.allowedMethods = new HashSet<HttpString>(allowedMethods);
        this.next = next;
    }

    public AllowedMethodsHandler(final HttpHandler next, final HttpString... allowedMethods) {
        this.allowedMethods = new HashSet<HttpString>(Arrays.asList(allowedMethods));
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (allowedMethods.contains(exchange.getRequestMethod())) {
            next.handleRequest(exchange);
        } else {
            exchange.setResponseCode(StatusCodes.METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }

}
