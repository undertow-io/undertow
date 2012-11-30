package io.undertow.servlet.handlers.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.servlet.annotation.ServletSecurity;

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.servlet.handlers.ServletAttachments;

/**
 * @author Stuart Douglas
 */
public class ServletSecurityConstraintHandler implements HttpHandler {

    private final SecurityPathMatches securityPathMatches;
    private final HttpHandler next;

    public ServletSecurityConstraintHandler(final SecurityPathMatches securityPathMatches, final HttpHandler next) {
        this.securityPathMatches = securityPathMatches;
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
        final String path = exchange.getRelativePath();
        SecurityPathMatch securityMatch = securityPathMatches.getSecurityInfo(path, exchange.getRequestMethod().toString());
        List<Set<String>> list = exchange.getAttachment(ServletAttachments.REQUIRED_ROLES);
        if(list == null) {
            exchange.putAttachment(ServletAttachments.REQUIRED_ROLES, list = new ArrayList<Set<String>>());
        }
        list.addAll(securityMatch.getRequiredRoles());
        ServletSecurity.TransportGuarantee type = exchange.getAttachment(ServletAttachments.TRANSPORT_GUARANTEE_TYPE);
        if(type == null || type.ordinal() < securityMatch.getTransportGuaranteeType().ordinal()) {
            exchange.putAttachment(ServletAttachments.TRANSPORT_GUARANTEE_TYPE, type);
        }
        HttpHandlers.executeHandler(next, exchange, completionHandler);
    }
}
