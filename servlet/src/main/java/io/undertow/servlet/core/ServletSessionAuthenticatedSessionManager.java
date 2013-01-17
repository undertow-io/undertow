package io.undertow.servlet.core;

import java.security.Principal;

import javax.servlet.http.HttpSession;

import io.undertow.security.api.AuthenticatedSessionManager;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.UndertowPrincipal;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.spec.ServletContextImpl;

/**
 * @author Stuart Douglas
 */
public class ServletSessionAuthenticatedSessionManager implements AuthenticatedSessionManager {

    private final ServletContextImpl servletContext;

    private static final String ATTRIBUTE_NAME = ServletSessionAuthenticatedSessionManager.class.getName() + ".userName";

    public ServletSessionAuthenticatedSessionManager(final ServletContextImpl servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public void userAuthenticated(final HttpServerExchange exchange, final Principal principal, final Account account) {
        HttpSession session = servletContext.getSession(exchange, true);
        session.setAttribute(ATTRIBUTE_NAME, account.getName());
    }

    @Override
    public void userLoggedOut(final HttpServerExchange exchange, final Principal principal, final Account account) {
        HttpSession session = servletContext.getSession(exchange, false);
        if(session != null) {
            session.removeAttribute(ATTRIBUTE_NAME);
        }

    }

    @Override
    public AuthenticationMechanism.AuthenticationMechanismResult lookupSession(final HttpServerExchange exchange, final IdentityManager identityManager) {
        HttpSession session = servletContext.getSession(exchange, false);
        if(session != null) {
            Object name = session.getAttribute(ATTRIBUTE_NAME);
            if(name != null) {
                Account account = identityManager.lookupAccount(name.toString());
                if(account != null) {
                    UndertowPrincipal principal = new UndertowPrincipal(account);
                    return new AuthenticationMechanism.AuthenticationMechanismResult(principal, account, true);
                }
            }
        }
        return new AuthenticationMechanism.AuthenticationMechanismResult(AuthenticationMechanism.AuthenticationMechanismOutcome.NOT_ATTEMPTED);
    }
}
