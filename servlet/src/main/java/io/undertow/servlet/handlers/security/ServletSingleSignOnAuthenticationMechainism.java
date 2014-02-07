package io.undertow.servlet.handlers.security;

import io.undertow.security.impl.SingleSignOnAuthenticationMechanism;
import io.undertow.security.impl.SingleSignOnManager;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;

import java.security.AccessController;

/**
 * Servlet version of the single sign on authentication mechanism.
 *
 * @author Stuart Douglas
 */
public class ServletSingleSignOnAuthenticationMechainism extends SingleSignOnAuthenticationMechanism {
    public ServletSingleSignOnAuthenticationMechainism(SingleSignOnManager storage) {
        super(storage);
    }

    @Override
    protected Session getSession(HttpServerExchange exchange) {
        ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        final HttpSessionImpl session = servletRequestContext.getCurrentServletContext().getSession(exchange, true);
        if(System.getSecurityManager() == null) {
            return session.getSession();
        } else {
            return AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(session));
        }
    }
}
