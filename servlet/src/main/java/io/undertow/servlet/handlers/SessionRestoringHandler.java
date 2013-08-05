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

import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionEvent;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A handler that restores persistent HTTP session state for requests in development mode.
 * <p/>
 * This handler should not be used in production environments.
 *
 * @author Stuart Douglas
 */
public class SessionRestoringHandler implements HttpHandler, Lifecycle {

    private final String deploymentName;
    private final Map<String, Map<String, Object>> data;
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
        this.data = new ConcurrentHashMap<String, Map<String, Object>>();
    }

    public void start() {
        ClassLoader old = getTccl();
        try {
            setTccl(servletContext.getClassLoader());

            try {
                final Map<String, Map<String, Object>> sessionData = sessionPersistenceManager.loadSessionAttributes(deploymentName, servletContext.getClassLoader());
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
            final Map<String, Map<String, Object>> objectData = new HashMap<String, Map<String, Object>>();
            for (String sessionId : sessionManager.getTransientSessions()) {
                Session session = sessionManager.getSession(sessionId);
                if (session != null) {
                    final HttpSessionEvent event = new HttpSessionEvent(HttpSessionImpl.forSession(session, servletContext, false));
                    final Map<String, Object> sessionData = new HashMap<String, Object>();
                    for (String attr : session.getAttributeNames()) {
                        final Object attribute = session.getAttribute(attr);
                        sessionData.put(attr, attribute);
                        if (attribute instanceof HttpSessionActivationListener) {
                            ((HttpSessionActivationListener) attribute).sessionWillPassivate(event);
                        }
                    }
                    objectData.put(sessionId, sessionData);
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
        Map<String, Object> result = data.remove(incomingSessionId);
        if (result != null) {
            final HttpSessionImpl session = servletContext.getSession(exchange, true);
            final HttpSessionEvent event = new HttpSessionEvent(session);
            for (Map.Entry<String, Object> entry : result.entrySet()) {

                if (entry.getValue() instanceof HttpSessionActivationListener) {
                    ((HttpSessionActivationListener) entry.getValue()).sessionWillPassivate(event);
                }
                session.setAttribute(entry.getKey(), entry.getValue());
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
