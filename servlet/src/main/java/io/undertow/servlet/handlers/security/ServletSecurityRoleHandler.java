package io.undertow.servlet.handlers.security;

import java.security.Principal;
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
    private final Map<String, Set<String>> roleVsPrincipleMappings;

    public ServletSecurityRoleHandler(final BlockingHttpHandler next, final Map<String, Set<String>> principleVsRoleMappings) {
        this.next = next;
        this.principleVsRoleMappings = principleVsRoleMappings;
        final Map<String, Set<String>> roleVsPrincipleMappings = new HashMap<String, Set<String>>();
        for(Map.Entry<String, Set<String>> entry : principleVsRoleMappings.entrySet()) {
            for(String val : entry.getValue()) {
                Set<String> principles = roleVsPrincipleMappings.get(val);
                if(principles == null) {
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
        HttpServletRequest request = HttpServletRequestImpl.getRequestImpl(exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY));
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            next.handleRequest(exchange);
        } else if (roles.isEmpty()) {
            next.handleRequest(exchange);
        } else {
            assert sc.getAuthenticationState() == AuthenticationState.AUTHENTICATED;
            for (final Set<String> roleSet : roles) {
                final Set<String> groups = getGroups(sc, roleSet);
                boolean found = false;
                for (String role : groups) {
                    if (sc.isUserInRole(role)) {
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

    /*
     * Return a set of underlying groups that the user must belong too
     */
    public Set<String> getGroups(final SecurityContext context, final Set<String> roles) {
        final Set<String> groups = new HashSet<String>();
        Principal principle = context.getAuthenticatedPrincipal();
        Set<String> principleGroups = principleVsRoleMappings.get(principle.getName());
        if (principleGroups != null) {
            groups.addAll(principleGroups);
        }
        for (final String role : roles) {
            Set<String> groupRoles = roleVsPrincipleMappings.get(role);
            if (groupRoles != null) {
                groups.addAll(groupRoles);
            } else {
                //nothing mapped, so we just use the role name directly
                groups.add(role);
            }
        }
        return groups;
    }
}
