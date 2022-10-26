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

package io.undertow.servlet.core;

import java.security.AccessController;
import java.util.HashSet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionListener;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.ThreadSetupHandler;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.servlet.spec.ServletContextImpl;

/**
 * Class that bridges between Undertow native session listeners and servlet ones.
 *
 * @author Stuart Douglas
 */
public class SessionListenerBridge implements SessionListener {

    public static final String IO_UNDERTOW = "io.undertow";
    private final ApplicationListeners applicationListeners;
    private final ServletContext servletContext;
    private final ThreadSetupHandler.Action<Void, Session> destroyedAction;

    public SessionListenerBridge(final Deployment deployment, final ApplicationListeners applicationListeners, final ServletContext servletContext) {
        this.applicationListeners = applicationListeners;
        this.servletContext = servletContext;
        this.destroyedAction = deployment.createThreadSetupAction(new ThreadSetupHandler.Action<Void, Session>() {
            @Override
            public Void call(HttpServerExchange exchange, Session session) throws ServletException {
                doDestroy(session);
                return null;
            }
        });
    }


    @Override
    public void sessionCreated(final Session session, final HttpServerExchange exchange) {
        ServletContext sc = servletContext;
        if (servletContext instanceof ServletContextImpl)
            sc = exchange.removeAttachment(((ServletContextImpl) servletContext).contextAttachmentKey);
        final HttpSessionImpl httpSession = SecurityActions.forSession(session, sc, true);
        applicationListeners.sessionCreated(httpSession);
    }

    @Override
    public void sessionDestroyed(final Session session, final HttpServerExchange exchange, final SessionDestroyedReason reason) {

        if (reason == SessionDestroyedReason.TIMEOUT) {
            try {
                //we need to perform thread setup actions
                destroyedAction.call(exchange, session);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            doDestroy(session);
        }

        ServletRequestContext current = SecurityActions.currentServletRequestContext();
        Session underlying = null;
        if (current != null && current.getSession() != null) {
            if (System.getSecurityManager() == null) {
                underlying = current.getSession().getSession();
            } else {
                underlying = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(current.getSession()));
            }
        }

        if (current != null && underlying == session) {
            current.setSession(null);
        }
    }

    private void doDestroy(Session session) {
        final HttpSessionImpl httpSession = SecurityActions.forSession(session, servletContext, false);
        applicationListeners.sessionDestroyed(httpSession);
        //we make a defensive copy here, as there is no guarantee that the underlying session map
        //is a concurrent map, and as a result a concurrent modification exception may be thrown
        HashSet<String> names = new HashSet<>(session.getAttributeNames());
        for (String attribute : names) {
            session.removeAttribute(attribute);
        }
    }

    @Override
    public void attributeAdded(final Session session, final String name, final Object value) {
        if (name.startsWith(IO_UNDERTOW)) {
            return;
        }
        final HttpSessionImpl httpSession = SecurityActions.forSession(session, servletContext, false);
        applicationListeners.httpSessionAttributeAdded(httpSession, name, value);
        if (value instanceof HttpSessionBindingListener) {
            ((HttpSessionBindingListener) value).valueBound(new HttpSessionBindingEvent(httpSession, name, value));
        }
    }

    @Override
    public void attributeUpdated(final Session session, final String name, final Object value, final Object old) {
        if (name.startsWith(IO_UNDERTOW)) {
            return;
        }
        final HttpSessionImpl httpSession = SecurityActions.forSession(session, servletContext, false);
        if (old != value) {
            if (old instanceof HttpSessionBindingListener) {
                ((HttpSessionBindingListener) old).valueUnbound(new HttpSessionBindingEvent(httpSession, name, old));
            }
            applicationListeners.httpSessionAttributeReplaced(httpSession, name, old);
        }
        if (value instanceof HttpSessionBindingListener) {
            ((HttpSessionBindingListener) value).valueBound(new HttpSessionBindingEvent(httpSession, name, value));
        }
    }

    @Override
    public void attributeRemoved(final Session session, final String name, final Object old) {
        if (name.startsWith(IO_UNDERTOW)) {
            return;
        }
        final HttpSessionImpl httpSession = SecurityActions.forSession(session, servletContext, false);
        if (old != null) {
            applicationListeners.httpSessionAttributeRemoved(httpSession, name, old);
            if (old instanceof HttpSessionBindingListener) {
                ((HttpSessionBindingListener) old).valueUnbound(new HttpSessionBindingEvent(httpSession, name, old));
            }
        }
    }

    @Override
    public void sessionIdChanged(Session session, String oldSessionId) {
        final HttpSessionImpl httpSession = SecurityActions.forSession(session, servletContext, true);
        applicationListeners.httpSessionIdChanged(httpSession, oldSessionId);
    }
}
