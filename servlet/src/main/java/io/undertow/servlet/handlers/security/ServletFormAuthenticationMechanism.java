package io.undertow.servlet.handlers.security;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.undertow.security.impl.FormAuthenticationMechanism;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;

/**
 * Servlet handler for FORM authentication. Instead of using a redirect it
 * serves up error and login pages immediately using a forward
 *
 * @author Stuart Douglas
 */
public class ServletFormAuthenticationMechanism extends FormAuthenticationMechanism {
    public ServletFormAuthenticationMechanism(final String name, final String loginPage, final String errorPage) {
        super(name, loginPage, errorPage);
    }

    public ServletFormAuthenticationMechanism(final String name, final String loginPage, final String errorPage, final String postLocation) {
        super(name, loginPage, errorPage, postLocation);
    }

    @Override
    protected Integer servePage(final HttpServerExchange exchange, final String location) {
        ServletRequest req = exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        ServletResponse resp = exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        RequestDispatcher disp = req.getRequestDispatcher(location);
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
        HttpServletRequest req = (HttpServletRequest) exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        HttpServletResponse resp = (HttpServletResponse) exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        final Cookie cookie = new Cookie(LOCATION_COOKIE, req.getContextPath() + req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo()));
        cookie.setPath(req.getServletContext().getContextPath());
        resp.addCookie(cookie);
    }

    @Override
    protected void handleRedirectBack(final HttpServerExchange exchange) {
        HttpServletRequest req = (HttpServletRequest) exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        HttpServletResponse resp = (HttpServletResponse) exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        Cookie[] cookies = req.getCookies();
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(LOCATION_COOKIE)) {
                try {
                    resp.sendRedirect(cookie.getValue());
                    return;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
