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

package io.undertow.servlet.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.api.SessionPersistenceManager;
import io.undertow.servlet.core.Lifecycle;
import io.undertow.servlet.spec.HttpSessionImpl;
import io.undertow.servlet.spec.ServletContextImpl;

import jakarta.servlet.http.HttpSessionActivationListener;
import jakarta.servlet.http.HttpSessionEvent;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.undertow.servlet.api.SessionPersistenceManager.PersistentSession;

/**
 * A handler that restores persistent HTTP session state for requests in development mode.
 * <p>
 * This handler should not be used in production environments.
 *
 * @author Stuart Douglas
 */
public class SessionRestoringHandler implements HttpHandler, Lifecycle {

    private final String deploymentName;
    private final Map<String, SessionPersistenceManager.PersistentSession> data;
    private final SessionManager sessionManager;
    private final ServletContextImpl servletContext;
    private final HttpHandler next;
    private final SessionPersistenceManager sessionPersistenceManager;
    private volatile boolean started = false;

    public SessionRestoringHandler(String deploymentName, SessionManager sessionManager, ServletContextImpl servletContext, HttpHandler next, SessionPersistenceManager sessionPersistenceManager) {
        this.deploymentName = deploymentName;
        this.sessionManager = sessionManager;
        this.servletContext = servletContext;
        this.next = next;
        this.sessionPersistenceManager = sessionPersistenceManager;
        this.data = new ConcurrentHashMap<>();
    }

    public void start() {
        ClassLoader old = getTccl();
        try {
            setTccl(servletContext.getClassLoader());

            try {
                final Map<String, SessionPersistenceManager.PersistentSession> sessionData = sessionPersistenceManager.loadSessionAttributes(deploymentName, servletContext.getClassLoader());
                if (sessionData != null) {
                    this.data.putAll(sessionData);
                }
            } catch (Exception e) {
                UndertowServletLogger.ROOT_LOGGER.failedtoLoadPersistentSessions(e);
            }
            this.started = true;
        } finally {
            setTccl(old);
        }
    }

    public void stop() {
        ClassLoader old = getTccl();
        try {
            setTccl(servletContext.getClassLoader());
            this.started = false;
            final Map<String, SessionPersistenceManager.PersistentSession> objectData = new HashMap<>();
            for (String sessionId : sessionManager.getTransientSessions()) {
                Session session = sessionManager.getSession(sessionId);
                if (session != null) {
                    final HttpSessionEvent event = new HttpSessionEvent(SecurityActions.forSession(session, servletContext, false));
                    final Map<String, Object> sessionData = new HashMap<>();
                    for (String attr : session.getAttributeNames()) {
                        final Object attribute = session.getAttribute(attr);
                        sessionData.put(attr, attribute);
                        if (attribute instanceof HttpSessionActivationListener) {
                            ((HttpSessionActivationListener) attribute).sessionWillPassivate(event);
                        }
                    }
                    objectData.put(sessionId, new PersistentSession(new Date(session.getLastAccessedTime() + (session.getMaxInactiveInterval() * 1000)), sessionData));
                }
            }
            sessionPersistenceManager.persistSessions(deploymentName, objectData);
            this.data.clear();
        } finally {
            setTccl(old);
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final String incomingSessionId = servletContext.getSessionConfig().findSessionId(exchange);
        if (incomingSessionId == null || !data.containsKey(incomingSessionId)) {
            next.handleRequest(exchange);
            return;
        }

        //we have some old data
        PersistentSession result = data.remove(incomingSessionId);
        if (result != null) {
            long time = System.currentTimeMillis();
            if (time < result.getExpiration().getTime()) {
                final HttpSessionImpl session = servletContext.getSession(exchange, true);
                final HttpSessionEvent event = new HttpSessionEvent(session);
                for (Map.Entry<String, Object> entry : result.getSessionData().entrySet()) {

                    if (entry.getValue() instanceof HttpSessionActivationListener) {
                        ((HttpSessionActivationListener) entry.getValue()).sessionDidActivate(event);
                    }
                    if(entry.getKey().startsWith(HttpSessionImpl.IO_UNDERTOW)) {
                        session.getSession().setAttribute(entry.getKey(), entry.getValue());
                    } else {
                        session.setAttribute(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        next.handleRequest(exchange);
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    private ClassLoader getTccl() {
        if (System.getSecurityManager() == null) {
            return Thread.currentThread().getContextClassLoader();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                @Override
                public ClassLoader run() {
                    return Thread.currentThread().getContextClassLoader();
                }
            });
        }
    }

    private void setTccl(final ClassLoader classLoader) {
        if (System.getSecurityManager() == null) {
            Thread.currentThread().setContextClassLoader(classLoader);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    Thread.currentThread().setContextClassLoader(classLoader);
                    return null;
                }
            });
        }
    }
}
