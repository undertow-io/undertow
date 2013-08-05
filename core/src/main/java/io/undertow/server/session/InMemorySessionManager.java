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

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * The default in memory session manager. This basically just stores sessions in an in memory hash map.
 * <p/>
 *
 * @author Stuart Douglas
 */
public class InMemorySessionManager implements SessionManager {

    private volatile SessionIdGenerator sessionIdGenerator = new SecureRandomSessionIdGenerator();

    private final ConcurrentMap<String, InMemorySession> sessions = new ConcurrentHashMap<String, InMemorySession>();

    private final SessionListeners sessionListeners = new SessionListeners();

    /**
     * 30 minute default
     */
    private volatile int defaultSessionTimeout = 30 * 60;

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
        if (config == null) {
            throw UndertowMessages.MESSAGES.couldNotFindSessionCookieConfig();
        }
        String sessionID = sessionIdGenerator.createSessionId();
        final SessionImpl session = new SessionImpl(sessionID, config, serverExchange.getIoThread(), serverExchange.getConnection().getWorker());
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
    public int activeSessions() {
        return sessions.size();
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

    /**
     * session implementation for the in memory session manager
     */
    private class SessionImpl implements Session {

        private String sessionId;
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

        private SessionImpl(final String sessionId, final SessionConfig sessionCookieConfig, final XnioExecutor executor, final XnioWorker worker) {
            this.sessionId = sessionId;
            this.sessionCookieConfig = sessionCookieConfig;
            this.executor = executor;
            this.worker = worker;
        }

        synchronized void bumpTimeout() {
            if (cancelKey != null) {
                if (!cancelKey.remove()) {
                    return;
                }
            }
            if(getMaxInactiveInterval() > 0) {
                cancelKey = executor.executeAfter(cancelTask, getMaxInactiveInterval(), TimeUnit.SECONDS);
            }
        }


        @Override
        public String getId() {
            return sessionId;
        }

        @Override
        public void requestDone(final HttpServerExchange serverExchange) {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess != null) {
                sess.lastAccessed = System.currentTimeMillis();
            }
        }

        @Override
        public long getCreationTime() {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return sess.creationTime;
        }

        @Override
        public long getLastAccessedTime() {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return sess.lastAccessed;
        }

        @Override
        public void setMaxInactiveInterval(final int interval) {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            sess.maxInactiveInterval = interval;
            bumpTimeout();
        }

        @Override
        public int getMaxInactiveInterval() {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return sess.maxInactiveInterval;
        }

        @Override
        public Object getAttribute(final String name) {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            bumpTimeout();
            return sess.attributes.get(name);
        }

        @Override
        public Set<String> getAttributeNames() {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            bumpTimeout();
            return sess.attributes.keySet();
        }

        @Override
        public Object setAttribute(final String name, final Object value) {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            final Object existing = sess.attributes.put(name, value);
            if (existing == null) {
                sessionListeners.attributeAdded(sess.session, name, value);
            } else {
                sessionListeners.attributeUpdated(sess.session, name, value, existing);
            }
            bumpTimeout();
            return existing;
        }

        @Override
        public Object removeAttribute(final String name) {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            final Object existing = sess.attributes.remove(name);
            sessionListeners.attributeRemoved(sess.session, name, existing);
            bumpTimeout();
            return existing;
        }

        @Override
        public void invalidate(final HttpServerExchange exchange) {
            invalidate(exchange, SessionListener.SessionDestroyedReason.INVALIDATED);
        }

        void invalidate(final HttpServerExchange exchange, SessionListener.SessionDestroyedReason reason) {
            if (cancelKey != null) {
                cancelKey.remove();
            }
            InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                if (reason == SessionListener.SessionDestroyedReason.INVALIDATED) {
                    throw UndertowMessages.MESSAGES.sessionAlreadyInvalidated();
                }
                return;
            }
            sessionListeners.sessionDestroyed(sess.session, exchange, reason);
            sessions.remove(sessionId);
            if (exchange != null) {
                sessionCookieConfig.clearSession(exchange, this.getId());
            }
        }

        @Override
        public SessionManager getSessionManager() {
            return InMemorySessionManager.this;
        }

        @Override
        public String changeSessionId(final HttpServerExchange exchange, final SessionConfig config) {
            final String oldId = sessionId;
            final InMemorySession sess = sessions.get(oldId);
            String newId = sessionIdGenerator.createSessionId();
            this.sessionId = newId;
            sessions.put(newId, sess);
            sessions.remove(oldId);
            config.setSessionId(exchange, this.getId());
            sessionListeners.sessionIdChanged(sess.session, oldId);
            return newId;
        }

        private void destroy() {
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
