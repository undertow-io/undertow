package io.undertow.servlet.handlers.security;

import java.util.List;
import java.util.Set;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.servlet.handlers.ServletAttachments;

/**
 * A simple handler that just sets the auth type to REQUIRED if required roles exists and is non-empty,
 * and does not contain any precluded elements (i.e. empty sets)
 *
 * @author Stuart Douglas
 */
public class ServletAuthenticationConstraintHandler extends AuthenticationConstraintHandler {

    public ServletAuthenticationConstraintHandler(final HttpHandler next) {
        super(next);
    }

    @Override
    protected boolean isAuthenticationRequired(final HttpServerExchange exchange) {
        List<Set<String>> roles = exchange.getAttachmentList(ServletAttachments.REQUIRED_ROLES);
        if (roles.isEmpty()) {
            return false;
        }
        for(Set<String> role : roles) {
            if(role.isEmpty()) {
                //this is an empty required role set, so this means this request has been denied
                //so there is no point authenticating as it will not help matters
                return false;
            }
        }
        return true;
    }

}
