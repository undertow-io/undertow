package io.undertow.servlet.handlers.security;

import javax.servlet.DispatcherType;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;

/**
 *
 * Wrapper handler that makes sure that the security handler chain only gets invoked on the first request.
 *
 * @author Stuart Douglas
 */
public class ServletSecurityWrapper implements HttpHandler {

    private final HttpHandler next;
    private final HttpHandler securityChain;

    public ServletSecurityWrapper(final HttpHandler next, final HttpHandler securityChain) {
        this.next = next;
        this.securityChain = securityChain;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if(exchange.getAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY) == DispatcherType.REQUEST) {
            securityChain.handleRequest(exchange);
        } else {
            next.handleRequest(exchange);
        }
    }
}
