package io.undertow.servlet.handlers.security;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.impl.FormAuthenticationMechanism;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.session.Session;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.servlet.util.SavedRequest;
import io.undertow.util.Methods;
import io.undertow.util.RedirectBuilder;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.AccessController;
import java.util.Map;

/**
 * Servlet handler for FORM authentication. Instead of using a redirect it
 * serves up error and login pages immediately using a forward
 *
 * @author Stuart Douglas
 */
public class ServletFormAuthenticationMechanism extends FormAuthenticationMechanism {

    private static final String SESSION_KEY = "io.undertow.servlet.form.auth.redirect.location";

    public static final Factory FACTORY = new Factory();

    @Deprecated
    public ServletFormAuthenticationMechanism(final String name, final String loginPage, final String errorPage) {
        super(name, loginPage, errorPage);
    }

    @Deprecated
    public ServletFormAuthenticationMechanism(final String name, final String loginPage, final String errorPage, final String postLocation) {
        super(name, loginPage, errorPage, postLocation);
    }

    public ServletFormAuthenticationMechanism(FormParserFactory formParserFactory, String name, String loginPage, String errorPage, String postLocation) {
        super(formParserFactory, name, loginPage, errorPage, postLocation);
    }

    public ServletFormAuthenticationMechanism(FormParserFactory formParserFactory, String name, String loginPage, String errorPage) {
        super(formParserFactory, name, loginPage, errorPage);
    }

    @Override
    protected Integer servePage(final HttpServerExchange exchange, final String location) {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        ServletRequest req = servletRequestContext.getServletRequest();
        ServletResponse resp = servletRequestContext.getServletResponse();
        RequestDispatcher disp = req.getRequestDispatcher(location);
        exchange.setRequestMethod(Methods.GET); //TODO: is this correct?
        try {
            disp.forward(req, resp);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    protected void storeInitialLocation(final HttpServerExchange exchange) {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        HttpSessionImpl httpSession = servletRequestContext.getCurrentServletContext().getSession(exchange, true);
        Session session;
        if (System.getSecurityManager() == null) {
            session = httpSession.getSession();
        } else {
            session = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(httpSession));
        }
        session.setAttribute(SESSION_KEY, RedirectBuilder.redirect(exchange, exchange.getRelativePath()));
        SavedRequest.trySaveRequest(exchange);
    }

    @Override
    protected void handleRedirectBack(final HttpServerExchange exchange) {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        HttpServletResponse resp = (HttpServletResponse) servletRequestContext.getServletResponse();
        HttpSessionImpl httpSession = servletRequestContext.getCurrentServletContext().getSession(exchange, false);
        if (httpSession != null) {
            Session session;
            if (System.getSecurityManager() == null) {
                session = httpSession.getSession();
            } else {
                session = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(httpSession));
            }
            String path = (String) session.getAttribute(SESSION_KEY);
            if (path != null) {
                try {
                    resp.sendRedirect(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    public static class Factory implements AuthenticationMechanismFactory {
        @Override
        public AuthenticationMechanism create(String mechanismName, FormParserFactory formParserFactory, Map<String, String> properties) {
            return new ServletFormAuthenticationMechanism(formParserFactory, mechanismName, properties.get(LOGIN_PAGE), properties.get(ERROR_PAGE));
        }
    }
}
