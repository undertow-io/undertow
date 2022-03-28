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

import io.undertow.security.api.AuthenticatedSessionManager;
import io.undertow.security.api.AuthenticatedSessionManager.AuthenticatedSession;
import io.undertow.security.api.NotificationReceiver;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.api.SecurityNotification;
import io.undertow.security.api.SecurityNotification.EventType;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.servlet.util.SavedRequest;

import java.security.AccessController;

import jakarta.servlet.http.HttpSession;

/**
 * {@link HttpHandler} responsible for setting up the {@link AuthenticatedSessionManager} for cached authentications and
 * registering a {@link NotificationReceiver} to receive the security notifications.
 *
 * This handler also forces the session to change its session ID on sucessful authentication.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class CachedAuthenticatedSessionHandler implements HttpHandler {

    public static final String ATTRIBUTE_NAME = CachedAuthenticatedSessionHandler.class.getName() + ".AuthenticatedSession";

    public static final String NO_ID_CHANGE_REQUIRED = CachedAuthenticatedSessionHandler.class.getName() + ".NoIdChangeRequired";

    private final NotificationReceiver NOTIFICATION_RECEIVER = new SecurityNotificationReceiver();
    private final AuthenticatedSessionManager SESSION_MANAGER = new ServletAuthenticatedSessionManager();

    private final HttpHandler next;
    private final ServletContextImpl servletContext;

    public CachedAuthenticatedSessionHandler(final HttpHandler next, final ServletContextImpl servletContext) {
        this.next = next;
        this.servletContext = servletContext;
    }


    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        SecurityContext securityContext = exchange.getSecurityContext();
        securityContext.registerNotificationReceiver(NOTIFICATION_RECEIVER);

        HttpSession session = servletContext.getSession(exchange, false);
        // If there was no existing HttpSession then there could not be a cached AuthenticatedSession so don't bother setting
        // the AuthenticatedSessionManager.
        if (session != null) {
            exchange.putAttachment(AuthenticatedSessionManager.ATTACHMENT_KEY, SESSION_MANAGER);
            SavedRequest.tryRestoreRequest(exchange, session); //not sure if this is where it belongs
        }

        next.handleRequest(exchange);
    }

    private class SecurityNotificationReceiver implements NotificationReceiver {

        @Override
        public void handleNotification(SecurityNotification notification) {
            EventType eventType = notification.getEventType();
            HttpSessionImpl httpSession = servletContext.getSession(notification.getExchange(), false);
            switch (eventType) {
                case AUTHENTICATED:
                    if (isCacheable(notification)) {
                        if(servletContext.getDeployment().getDeploymentInfo().isChangeSessionIdOnLogin()) {
                            if (httpSession != null) {
                                Session session = underlyingSession(httpSession);
                                if (!httpSession.isNew() &&
                                        !httpSession.isInvalid() &&
                                        session.getAttribute(NO_ID_CHANGE_REQUIRED) == null) {
                                    ServletRequestContext src = notification.getExchange().getAttachment(ServletRequestContext.ATTACHMENT_KEY);
                                    src.getOriginalRequest().changeSessionId();
                                }
                                if(session.getAttribute(NO_ID_CHANGE_REQUIRED) == null) {
                                    session.setAttribute(NO_ID_CHANGE_REQUIRED, true);
                                }
                            }
                        }
                        if(httpSession == null) {
                            httpSession = servletContext.getSession(notification.getExchange(), true);
                        }
                        Session session = underlyingSession(httpSession);

                        // It is normal for this notification to be received when using a previously cached session - in that
                        // case the IDM would have been given an opportunity to re-load the Account so updating here ready for
                        // the next request is desired.
                        session.setAttribute(ATTRIBUTE_NAME,
                                new AuthenticatedSession(notification.getAccount(), notification.getMechanism()));
                    }
                    break;
                case LOGGED_OUT:
                    if (httpSession != null) {
                        Session session = underlyingSession(httpSession);
                        session.removeAttribute(ATTRIBUTE_NAME);
                        session.removeAttribute(NO_ID_CHANGE_REQUIRED);
                    }
                    break;
            }
        }

    }

    protected Session underlyingSession(HttpSessionImpl httpSession) {
        Session session;
        if (System.getSecurityManager() == null) {
            session = httpSession.getSession();
        } else {
            session = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(httpSession));
        }
        return session;
    }

    private class ServletAuthenticatedSessionManager implements AuthenticatedSessionManager {

        @Override
        public AuthenticatedSession lookupSession(HttpServerExchange exchange) {
            HttpSessionImpl httpSession = servletContext.getSession(exchange, false);
            if (httpSession != null) {
                Session session = underlyingSession(httpSession);
                return (AuthenticatedSession) session.getAttribute(ATTRIBUTE_NAME);
            }
            return null;
        }

        @Override
        public void clearSession(HttpServerExchange exchange) {
            HttpSessionImpl httpSession = servletContext.getSession(exchange, false);
            if (httpSession != null) {
                Session session = underlyingSession(httpSession);
                session.removeAttribute(ATTRIBUTE_NAME);
            }
        }

    }

    private boolean isCacheable(final SecurityNotification notification) {
        return notification.isProgramatic() || notification.isCachingRequired();
    }

}
