package io.undertow.servlet.handlers.security;

import java.util.List;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.security.api.RoleMappingManager;
import io.undertow.security.impl.SecurityContext;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.handlers.ServletAttachments;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;

/**
 * Servlet role handler
 *
 * @author Stuart Douglas
 */
public class ServletSecurityRoleHandler implements BlockingHttpHandler {

    private final BlockingHttpHandler next;
    private final RoleMappingManager roleMappingManager;

    public ServletSecurityRoleHandler(final BlockingHttpHandler next, final RoleMappingManager roleMappingManager) {
        this.next = next;
        this.roleMappingManager = roleMappingManager;
    }

    @Override
    public void handleRequest(final BlockingHttpServerExchange exchange) throws Exception {
        List<Set<String>> roles = exchange.getExchange().getAttachmentList(ServletAttachments.REQUIRED_ROLES);
        SecurityContext sc = exchange.getExchange().getAttachment(SecurityContext.ATTACHMENT_KEY);
        exchange.getExchange().putAttachment(ServletAttachments.SERVLET_ROLE_MAPPINGS, roleMappingManager);
        HttpServletRequest request = HttpServletRequestImpl.getRequestImpl(exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY));
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            next.handleRequest(exchange);
        } else if (roles.isEmpty()) {
            next.handleRequest(exchange);
        } else {
            for (final Set<String> roleSet : roles) {
                boolean found = false;
                for (String role : roleSet) {
                    if (roleMappingManager.isUserInRole(role, sc)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    HttpServletResponse response = (HttpServletResponse) exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
                    response.sendError(403);
                    return;
                }
            }
            next.handleRequest(exchange);
        }
    }


}
