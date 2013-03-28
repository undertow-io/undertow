package io.undertow.servlet.handlers.security;

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletAttachments;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;

import java.util.List;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet role handler
 *
 * @author Stuart Douglas
 */
public class ServletSecurityRoleHandler implements HttpHandler {

    private final HttpHandler next;

    public ServletSecurityRoleHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        List<Set<String>> roles = exchange.getAttachmentList(ServletAttachments.REQUIRED_ROLES);
        SecurityContext sc = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        HttpServletRequest request = HttpServletRequestImpl.getRequestImpl(exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY));
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            next.handleRequest(exchange);
        } else if (roles == null || roles.isEmpty()) {
            next.handleRequest(exchange);
        } else {
            for (final Set<String> roleSet : roles) {
                boolean found = false;
                Account account = sc.getAuthenticatedAccount();
                for (String role : roleSet) {
                    if (account.isUserInRole(role)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    HttpServletResponse response = (HttpServletResponse) exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
                    response.sendError(403);
                    return;
                }
            }
            next.handleRequest(exchange);
        }
    }


}
