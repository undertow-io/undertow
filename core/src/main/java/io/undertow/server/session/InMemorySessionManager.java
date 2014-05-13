/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.session;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConcurrentDirectDeque;

import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * The default in memory session manager. This basically just stores sessions in an in memory hash map.
 * <p/>
 *
 * @author Stuart Douglas
 */
public class InMemorySessionManager implements SessionManager {

    private volatile SessionIdGenerator sessionIdGenerator = new SecureRandomSessionIdGenerator();

    private final ConcurrentMap<String, InMemorySession> sessions;

    private final SessionListeners sessionListeners = new SessionListeners();

    /**
     * 30 minute default
     */
    private volatile int defaultSessionTimeout = 30 * 60;

    private final int maxSize;

    private final ConcurrentDirectDeque<String> evictionQueue;

    private final String deploymentName;

    public InMemorySessionManager(String deploymentName, int maxSessions) {
        this.deploymentName = deploymentName;
        this.sessions = new ConcurrentHashMap<String, InMemorySession>();
        this.maxSize = maxSessions;
        ConcurrentDirectDeque<String> evictionQueue = null;
        if (maxSessions > 0) {
            evictionQueue = ConcurrentDirectDeque.newInstance();
        }
        this.evictionQueue = evictionQueue;
    }

    public InMemorySessionManager(String id) {
        this(id, -1);
    }

    @Override
    public String getDeploymentName() {
        return this.deploymentName;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {
        for (Map.Entry<String, InMemorySession> session : sessions.entrySet()) {
            session.getValue().session.destroy();
            sessionListeners.sessionDestroyed(session.getValue().session, null, SessionListener.SessionDestroyedReason.UNDEPLOY);
        }
        sessions.clear();
    }

    @Override
    public Session createSession(final HttpServerExchange serverExchange, final SessionConfig config) {
        if (evictionQueue != null) {
            while (sessions.size() >= maxSize && !evictionQueue.isEmpty()) {
                String key = evictionQueue.poll();
                UndertowLogger.REQUEST_LOGGER.debugf("Removing session %s as max size has been hit", key);
                InMemorySession toRemove = sessions.get(key);
                if (toRemove != null) {
                    toRemove.session.invalidate(null, SessionListener.SessionDestroyedReason.TIMEOUT); //todo: better reason
                }
            }
        }
        if (config == null) {
            throw UndertowMessages.MESSAGES.couldNotFindSessionCookieConfig();
        }
        String sessionID = config.findSessionId(serverExchange);
        int count = 0;
        while (sessionID == null) {
            sessionID = sessionIdGenerator.createSessionId();
            if(sessions.containsKey(sessionID)) {
                sessionID = null;
            }
            if(count++ == 100) {
                //this should never happen
                //but we guard against pathalogical session id generators to prevent an infinite loop
                throw UndertowMessages.MESSAGES.couldNotGenerateUniqueSessionId();
            }
        }
        Object evictionToken;
        if (evictionQueue != null) {
            evictionToken = evictionQueue.offerLastAndReturnToken(sessionID);
        } else {
            evictionToken = null;
        }
        final SessionImpl session = new SessionImpl(this, sessionID, config, serverExchange.getIoThread(), serverExchange.getConnection().getWorker(), evictionToken);
        InMemorySession im = new InMemorySession(session, defaultSessionTimeout);
        sessions.put(sessionID, im);
        config.setSessionId(serverExchange, session.getId());
        im.lastAccessed = System.currentTimeMillis();
        session.bumpTimeout();
        sessionListeners.sessionCreated(session, serverExchange);
        return session;
    }

    @Override
    public Session getSession(final HttpServerExchange serverExchange, final SessionConfig config) {
        String sessionId = config.findSessionId(serverExchange);
        return getSession(sessionId);
    }

    @Override
    public Session getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        final InMemorySession sess = sessions.get(sessionId);
        if (sess == null) {
            return null;
        } else {
            return sess.session;
        }
    }


    @Override
    public synchronized void registerSessionListener(final SessionListener listener) {
        sessionListeners.addSessionListener(listener);
    }

    @Override
    public synchronized void removeSessionListener(final SessionListener listener) {
        sessionListeners.removeSessionListener(listener);
    }

    @Override
    public void setDefaultSessionTimeout(final int timeout) {
        defaultSessionTimeout = timeout;
    }

    @Override
    public Set<String> getTransientSessions() {
        return getAllSessions();
    }

    @Override
    public Set<String> getActiveSessions() {
        return getAllSessions();
    }

    @Override
    public Set<String> getAllSessions() {
        return new HashSet<String>(sessions.keySet());
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof SessionManager)) return false;
        SessionManager manager = (SessionManager) object;
        return this.deploymentName.equals(manager.getDeploymentName());
    }

    @Override
    public int hashCode() {
        return this.deploymentName.hashCode();
    }

    @Override
    public String toString() {
        return this.deploymentName.toString();
    }

    /**
     * session implementation for the in memory session manager
     */
    private static class SessionImpl implements Session {

        private final InMemorySessionManager sessionManager;

        private static volatile AtomicReferenceFieldUpdater<SessionImpl, Object> evictionTokenUpdater = AtomicReferenceFieldUpdater.newUpdater(SessionImpl.class, Object.class, "evictionToken");

        private String sessionId;
        private volatile Object evictionToken;
        private final SessionConfig sessionCookieConfig;

        final XnioExecutor executor;
        final XnioWorker worker;

        XnioExecutor.Key cancelKey;

        Runnable cancelTask = new Runnable() {
            @Override
            public void run() {
                worker.execute(new Runnable() {
                    @Override
                    public void run() {
                        invalidate(null, SessionListener.SessionDestroyedReason.TIMEOUT);
                    }
                });
            }
        };

        private SessionImpl(InMemorySessionManager sessionManager, final String sessionId, final SessionConfig sessionCookieConfig, final XnioExecutor executor, final XnioWorker worker, final Object evictionToken) {
            this.sessionManager = sessionManager;
            this.sessionId = sessionId;
            this.sessionCookieConfig = sessionCookieConfig;
            this.executor = executor;
            this.worker = worker;
            this.evictionToken = evictionToken;
        }

        synchronized void bumpTimeout() {
            if (cancelKey != null) {
                if (!cancelKey.remove()) {
                    return;
                }
            }
            if (getMaxInactiveInterval() > 0) {
                cancelKey = executor.executeAfter(cancelTask, getMaxInactiveInterval(), TimeUnit.SECONDS);
            }
            if (evictionToken != null) {
                Object token = evictionToken;
                if (evictionTokenUpdater.compareAndSet(this, token, null)) {
                    sessionManager.evictionQueue.removeToken(token);
                    this.evictionToken = sessionManager.evictionQueue.offerLastAndReturnToken(sessionId);
                }
            }
        }


        @Override
        public String getId() {
            return sessionId;
        }

        @Override
        public void requestDone(final HttpServerExchange serverExchange) {
            final InMemorySession sess = sessionManager.sessions.get(sessionId);
            if (sess != null) {
                sess.lastAccessed = System.currentTimeMillis();
            }
        }

        @Override
        public long getCreationTime() {
            final InMemorySession sess = sessionManager.sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return sess.creationTime;
        }

        @Override
        public long getLastAccessedTime() {
            final InMemorySession sess = sessionManager.sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return sess.lastAccessed;
        }

        @Override
        public void setMaxInactiveInterval(final int interval) {
            final InMemorySession sess = sessionManager.sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            sess.maxInactiveInterval = interval;
            bumpTimeout();
        }

        @Override
        public int getMaxInactiveInterval() {
            final InMemorySession sess = sessionManager.sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return sess.maxInactiveInterval;
        }

        @Override
        public Object getAttribute(final String name) {
            final InMemorySession sess = sessionManager.sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            bumpTimeout();
            return sess.attributes.get(name);
        }

        @Override
        public Set<String> getAttributeNames() {
            final InMemorySession sess = sessionManager.sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            bumpTimeout();
            return sess.attributes.keySet();
        }

        @Override
        public Object setAttribute(final String name, final Object value) {
            final InMemorySession sess = sessionManager.sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            final Object existing = sess.attributes.put(name, value);
            if (existing == null) {
                sessionManager.sessionListeners.attributeAdded(sess.session, name, value);
            } else {
               sessionManager.sessionListeners.attributeUpdated(sess.session, name, value, existing);
            }
            bumpTimeout();
            return existing;
        }

        @Override
        public Object removeAttribute(final String name) {
            final InMemorySession sess = sessionManager.sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            final Object existing = sess.attributes.remove(name);
            sessionManager.sessionListeners.attributeRemoved(sess.session, name, existing);
            bumpTimeout();
            return existing;
        }

        @Override
        public void invalidate(final HttpServerExchange exchange) {
            invalidate(exchange, SessionListener.SessionDestroyedReason.INVALIDATED);
        }

        synchronized void invalidate(final HttpServerExchange exchange, SessionListener.SessionDestroyedReason reason) {
            if (cancelKey != null) {
                cancelKey.remove();
            }
            InMemorySession sess = sessionManager.sessions.get(sessionId);
            if (sess == null) {
                if (reason == SessionListener.SessionDestroyedReason.INVALIDATED) {
                    throw UndertowMessages.MESSAGES.sessionAlreadyInvalidated();
                }
                return;
            }
            sessionManager.sessionListeners.sessionDestroyed(sess.session, exchange, reason);
            sessionManager.sessions.remove(sessionId);
            if (exchange != null) {
                sessionCookieConfig.clearSession(exchange, this.getId());
            }
        }

        @Override
        public SessionManager getSessionManager() {
            return sessionManager;
        }

        @Override
        public String changeSessionId(final HttpServerExchange exchange, final SessionConfig config) {
            final String oldId = sessionId;
            final InMemorySession sess = sessionManager.sessions.get(oldId);
            String newId = sessionManager.sessionIdGenerator.createSessionId();
            this.sessionId = newId;
            sessionManager.sessions.put(newId, sess);
            sessionManager.sessions.remove(oldId);
            config.setSessionId(exchange, this.getId());
            sessionManager.sessionListeners.sessionIdChanged(sess.session, oldId);
            return newId;
        }

        private synchronized void destroy() {
            if (cancelKey != null) {
                cancelKey.remove();
            }
            cancelTask = null;
        }

    }

    /**
     * class that holds the real session data
     */
    private static class InMemorySession {

        final SessionImpl session;

        InMemorySession(final SessionImpl session, int maxInactiveInterval) {
            this.session = session;
            creationTime = lastAccessed = System.currentTimeMillis();
            this.maxInactiveInterval = maxInactiveInterval;
        }

        final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<String, Object>();
        volatile long lastAccessed;
        final long creationTime;
        volatile int maxInactiveInterval;
    }
}
