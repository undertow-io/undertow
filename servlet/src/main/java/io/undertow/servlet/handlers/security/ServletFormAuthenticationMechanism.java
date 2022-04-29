/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.servlet.handlers.security;

import static io.undertow.util.StatusCodes.OK;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanismFactory;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.FormAuthenticationMechanism;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionListener;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.servlet.util.SavedRequest;
import io.undertow.util.Headers;
import io.undertow.util.RedirectBuilder;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.security.AccessController;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Servlet handler for FORM authentication. Instead of using a redirect it
 * serves up error and login pages immediately using a forward
 *
 * @author Stuart Douglas
 */
public class ServletFormAuthenticationMechanism extends FormAuthenticationMechanism {

    public static final AuthenticationMechanismFactory FACTORY = new Factory();

    private static final String SESSION_KEY = "io.undertow.servlet.form.auth.redirect.location";

    public static final String SAVE_ORIGINAL_REQUEST = "save-original-request";

    private final boolean saveOriginalRequest;
    // Use weak references to prevent memory leaks following undeployment
    private final Set<SessionManager> seenSessionManagers = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<SessionManager, Boolean>()));

    private final String defaultPage;

    private final boolean overrideInitial;

    private static final SessionListener LISTENER = new SessionListener() {
        @Override
        public void sessionCreated(Session session, HttpServerExchange exchange) { }

        @Override
        public void sessionDestroyed(Session session, HttpServerExchange exchange, SessionDestroyedReason reason) { }

        @Override
        public void attributeAdded(Session session, String name, Object value) { }

        @Override
        public void attributeUpdated(Session session, String name, Object newValue, Object oldValue) { }

        @Override
        public void attributeRemoved(Session session, String name, Object oldValue) { }

        @Override
        public void sessionIdChanged(Session session, String oldSessionId) {
            String oldLocation = (String)session.getAttribute(SESSION_KEY);
            if(oldLocation != null) {
                //todo: in theory this could break if there are multiple path parameters
                //but this is such an edge case this is probably fine
                String oldPart = ";jsessionid=" + oldSessionId;
                if (oldLocation.contains(oldPart)) {
                    session.setAttribute(ServletFormAuthenticationMechanism.SESSION_KEY, oldLocation.replace(oldPart, ";jsessionid=" + session.getId()));
                }
            }
        }
    };

    @Deprecated
    public ServletFormAuthenticationMechanism(final String name, final String loginPage, final String errorPage) {
        super(name, loginPage, errorPage);
        this.saveOriginalRequest = true;
        this.defaultPage = null;
        this.overrideInitial = false;
    }

    @Deprecated
    public ServletFormAuthenticationMechanism(final String name, final String loginPage, final String errorPage, final String postLocation) {
        super(name, loginPage, errorPage, postLocation);
        this.saveOriginalRequest = true;
        this.defaultPage = null;
        this.overrideInitial = false;
    }

    public ServletFormAuthenticationMechanism(FormParserFactory formParserFactory, String name, String loginPage, String errorPage, String postLocation) {
        super(formParserFactory, name, loginPage, errorPage, postLocation);
        this.saveOriginalRequest = true;
        this.defaultPage = null;
        this.overrideInitial = false;
    }

    public ServletFormAuthenticationMechanism(FormParserFactory formParserFactory, String name, String loginPage, String errorPage) {
        super(formParserFactory, name, loginPage, errorPage);
        this.saveOriginalRequest = true;
        this.defaultPage = null;
        this.overrideInitial = false;
    }

    public ServletFormAuthenticationMechanism(FormParserFactory formParserFactory, String name, String loginPage, String errorPage, IdentityManager identityManager) {
        super(formParserFactory, name, loginPage, errorPage, identityManager);
        this.saveOriginalRequest = true;
        this.defaultPage = null;
        this.overrideInitial = false;
    }

    public ServletFormAuthenticationMechanism(FormParserFactory formParserFactory, String name, String loginPage, String errorPage, IdentityManager identityManager, boolean saveOriginalRequest) {
        super(formParserFactory, name, loginPage, errorPage, identityManager);
        this.saveOriginalRequest = true;
        this.defaultPage = null;
        this.overrideInitial = false;
    }

    public ServletFormAuthenticationMechanism(FormParserFactory formParserFactory, String name, String loginPage, String errorPage, String defaultPage, boolean overrideInitial, IdentityManager identityManager, boolean saveOriginalRequest) {
        super(formParserFactory, name, loginPage, errorPage, identityManager);
        this.saveOriginalRequest = saveOriginalRequest;
        this.defaultPage = defaultPage;
        this.overrideInitial = overrideInitial;
    }

    @Override
    protected Integer servePage(final HttpServerExchange exchange, final String location) {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        ServletRequest req = servletRequestContext.getServletRequest();
        ServletResponse resp = servletRequestContext.getServletResponse();
        RequestDispatcher disp = req.getRequestDispatcher(location);
        //make sure the login page is never cached
        exchange.getResponseHeaders().add(Headers.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        exchange.getResponseHeaders().add(Headers.PRAGMA, "no-cache");
        exchange.getResponseHeaders().add(Headers.EXPIRES, "0");

        final FormResponseWrapper respWrapper = exchange.getStatusCode() != OK && resp instanceof HttpServletResponse
                ? new FormResponseWrapper((HttpServletResponse) resp) : null;

        try {
            disp.forward(req, respWrapper != null ? respWrapper : resp);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return respWrapper != null ? respWrapper.getStatus() : null;
    }

    @Override
    protected void storeInitialLocation(final HttpServerExchange exchange) {
        storeInitialLocation(exchange, null, 0);
    }

    /**
     * This method doesn't save content of request but instead uses data from parameter.
     * This should be used in case that data from request was already read and therefore it is not possible to save them.
     *
     * @param exchange
     * @param bytes
     * @param contentLength
     */
    protected void storeInitialLocation(final HttpServerExchange exchange, byte[] bytes, int contentLength) {
        if(!saveOriginalRequest) {
            return;
        }
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        HttpSessionImpl httpSession = servletRequestContext.getCurrentServletContext().getSession(exchange, true);
        Session session;
        if (System.getSecurityManager() == null) {
            session = httpSession.getSession();
        } else {
            session = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(httpSession));
        }
        SessionManager manager = session.getSessionManager();
        if (seenSessionManagers.add(manager)) {
            manager.registerSessionListener(LISTENER);
        }
        session.setAttribute(SESSION_KEY, RedirectBuilder.redirect(exchange, exchange.getRelativePath()));
        if(bytes == null) {
            SavedRequest.trySaveRequest(exchange);
        } else {
            SavedRequest.trySaveRequest(exchange, bytes, contentLength);
        }
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
            if ((path == null || overrideInitial) && defaultPage != null) {
                path = defaultPage;
            }
            if (path != null) {
                try {
                    resp.sendRedirect(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    private static class FormResponseWrapper extends HttpServletResponseWrapper {

        private int status = OK;

        private FormResponseWrapper(final HttpServletResponse wrapped) {
            super(wrapped);
        }

        @Override
        public void setStatus(int sc) {
            status = sc;
        }

        @Override
        public int getStatus() {
            return status;
        }

    }

    public static class Factory implements AuthenticationMechanismFactory {

        @Deprecated
        public Factory(IdentityManager identityManager) {}

        public Factory() {}

        @Override
        public AuthenticationMechanism create(String mechanismName, IdentityManager identityManager, FormParserFactory formParserFactory, Map<String, String> properties) {
            final String loginPage = properties.get(LOGIN_PAGE);
            final String errorPage = properties.get(ERROR_PAGE);
            final String defaultPage = properties.get(DEFAULT_PAGE);
            final boolean overrideInitial = properties.containsKey(OVERRIDE_INITIAL) ?
                    Boolean.parseBoolean(properties.get(OVERRIDE_INITIAL)): false;
            boolean saveOriginal = true;
            if (properties.containsKey(SAVE_ORIGINAL_REQUEST)) {
                saveOriginal = Boolean.parseBoolean(properties.get(SAVE_ORIGINAL_REQUEST));
            }
            return new ServletFormAuthenticationMechanism(formParserFactory, mechanismName, loginPage, errorPage, defaultPage, overrideInitial, identityManager, saveOriginal);
        }
    }

}
