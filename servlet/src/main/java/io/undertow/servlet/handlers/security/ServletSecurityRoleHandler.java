package io.undertow.servlet.handlers.security;

import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.server.handlers.security.AuthenticationState;
import io.undertow.server.handlers.security.SecurityContext;
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
    private final Map<String, Set<String>> principleVsRoleMappings;

    public ServletSecurityRoleHandler(final BlockingHttpHandler next, final Map<String, Set<String>> principleVsRoleMappings) {
        this.next = next;
        this.principleVsRoleMappings = principleVsRoleMappings;
    }

    @Override
    public void handleRequest(final BlockingHttpServerExchange exchange) throws Exception {
        List<Set<String>> roles = exchange.getExchange().getAttachmentList(ServletAttachments.REQUIRED_ROLES);
        SecurityContext sc = exchange.getExchange().getAttachment(SecurityContext.ATTACHMENT_KEY);
        HttpServletRequest request = HttpServletRequestImpl.getRequestImpl(exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY));
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            next.handleRequest(exchange);
        } else if (roles.isEmpty()) {
            next.handleRequest(exchange);
        } else {
            assert sc.getAuthenticationState() == AuthenticationState.AUTHENTICATED;
            final Set<String> userRoles = getRoles(sc);
            for (final Set<String> roleSet : roles) {
                boolean found = false;
                for (String role : roleSet) {
                    if (userRoles.contains(role)) {
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

    public Set<String> getRoles(final SecurityContext context) {
        final Set<String> roles = new HashSet<String>();
        Principal principle = context.getAuthenticatedPrincipal();
        Set<String> pricipleRoles = principleVsRoleMappings.get(principle.getName());
        if (pricipleRoles != null) {
            roles.addAll(pricipleRoles);
        }
        for (final String role : context.getAuthenticatedRoles()) {
            Set<String> groupRoles = principleVsRoleMappings.get(role);
            if (groupRoles != null) {
                roles.addAll(groupRoles);
            }
        }
        return roles;
    }
}
