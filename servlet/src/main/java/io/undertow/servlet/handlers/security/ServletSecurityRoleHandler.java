package io.undertow.servlet.handlers.security;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.security.impl.SecurityContext;
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
    private final Map<String, Set<String>> roleVsPrincipleMappings;

    public ServletSecurityRoleHandler(final BlockingHttpHandler next, final Map<String, Set<String>> principleVsRoleMappings) {
        this.next = next;
        this.principleVsRoleMappings = principleVsRoleMappings;
        final Map<String, Set<String>> roleVsPrincipleMappings = new HashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> entry : principleVsRoleMappings.entrySet()) {
            for (String val : entry.getValue()) {
                Set<String> principles = roleVsPrincipleMappings.get(val);
                if (principles == null) {
                    roleVsPrincipleMappings.put(val, principles = new HashSet<String>());
                }
                principles.add(entry.getKey());
            }
        }
        this.roleVsPrincipleMappings = roleVsPrincipleMappings;
    }

    @Override
    public void handleRequest(final BlockingHttpServerExchange exchange) throws Exception {
        List<Set<String>> roles = exchange.getExchange().getAttachmentList(ServletAttachments.REQUIRED_ROLES);
        SecurityContext sc = exchange.getExchange().getAttachment(SecurityContext.ATTACHMENT_KEY);
        final ServletRoleMappings mappings = new ServletRoleMappings(sc, principleVsRoleMappings, roleVsPrincipleMappings);
        exchange.getExchange().putAttachment(ServletAttachments.SERVLET_ROLE_MAPPINGS, mappings);
        HttpServletRequest request = HttpServletRequestImpl.getRequestImpl(exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY));
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            next.handleRequest(exchange);
        } else if (roles.isEmpty()) {
            next.handleRequest(exchange);
        } else {
            for (final Set<String> roleSet : roles) {
                boolean found = false;
                for (String role : roleSet) {
                    if (mappings.isUserInRole(role)) {
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
