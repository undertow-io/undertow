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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.SecureHashMap;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;

/**
 * The default in memory session manager. This basically just stores sessions in an in memory hash map.
 * <p/>
 * TODO: implement session expiration
 *
 * @author Stuart Douglas
 */
public class InMemorySessionManager implements SessionManager {

    private volatile SessionIdGenerator sessionIdGenerator = new SecureRandomSessionIdGenerator();

    private final ConcurrentMap<String, InMemorySession> sessions = new SecureHashMap<>();

    private volatile List<SessionListener> listeners = Collections.emptyList();

    /**
     * 30 minute default
     */
    private volatile int defaultSessionTimeout = 30 * 60;

    @Override
    public Session createSession(final HttpServerExchange serverExchange, final SessionConfig config) {
        if (config == null) {
            throw UndertowMessages.MESSAGES.couldNotFindSessionCookieConfig();
        }
        String sessionID = config.findSessionId(serverExchange);
        if (sessionID != null) {
            InMemorySession session = sessions.get(sessionID);
            if (session != null) {
                throw UndertowMessages.MESSAGES.sessionAlreadyExists(sessionID);
            }
        } else {
            sessionID = sessionIdGenerator.createSessionId();
        }
        final SessionImpl session = new SessionImpl(sessionID, config, serverExchange.getWriteThread(), serverExchange.getConnection().getWorker());
        InMemorySession im = new InMemorySession(session, defaultSessionTimeout);
        sessions.put(sessionID, im);
        for (SessionListener listener : listeners) {
            listener.sessionCreated(session, serverExchange);
        }
        config.attachSession(serverExchange, session);
        im.lastAccessed = System.currentTimeMillis();
        session.bumpTimeout();
        return session;
    }

    @Override
    public Session getSession(final HttpServerExchange serverExchange, final SessionConfig config) {
        String sessionId = config.findSessionId(serverExchange);
        if (sessionId == null) {
            return null;
        }
        final InMemorySession sess = sessions.get(sessionId);
        if (sess == null) {
            return null;
        } else {
            config.attachSession(serverExchange, sess.session);
            return sess.session;
        }
    }


    @Override
    public synchronized void registerSessionListener(final SessionListener listener) {
        final List<SessionListener> listeners = new ArrayList<SessionListener>(this.listeners);
        listeners.add(listener);
        this.listeners = Collections.unmodifiableList(listeners);
    }

    @Override
    public synchronized void removeSessionListener(final SessionListener listener) {
        final List<SessionListener> listeners = new ArrayList<SessionListener>(this.listeners);
        listeners.remove(listener);
        this.listeners = Collections.unmodifiableList(listeners);
    }

    @Override
    public void setDefaultSessionTimeout(final int timeout) {
        defaultSessionTimeout = timeout;
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

        final Runnable cancelTask = new Runnable() {
            @Override
            public void run() {
                worker.execute(new Runnable() {
                    @Override
                    public void run() {
                        invalidate(null);
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
            cancelKey = executor.executeAfter(cancelTask, getMaxInactiveInterval(), TimeUnit.SECONDS);
        }


        @Override
        public String getId() {
            return sessionId;
        }

        @Override
        public void requestDone(final HttpServerExchange serverExchange) {
            //noop
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
            for (SessionListener listener : listeners) {
                if (existing == null) {
                    listener.attributeAdded(sess.session, name, value);
                } else {
                    listener.attributeUpdated(sess.session, name, value);
                }
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
            for (SessionListener listener : listeners) {
                listener.attributeRemoved(sess.session, name);
            }
            bumpTimeout();
            return existing;
        }

        @Override
        public void invalidate(final HttpServerExchange exchange) {
            final InMemorySession sess = sessions.remove(sessionId);
            if (sess == null) {
                throw UndertowMessages.MESSAGES.sessionAlreadyInvalidated();
            }
            for (SessionListener listener : listeners) {
                listener.sessionDestroyed(sess.session, exchange, false);
            }
            if (exchange != null) {
                sessionCookieConfig.clearSession(exchange, this);
            }
        }

        @Override
        public SessionManager getSessionManager() {
            return InMemorySessionManager.this;
        }

        @Override
        public void updateLastAccessedTime() {
            final InMemorySession sess = sessions.get(sessionId);
            if(sess != null) {
                sess.lastAccessed = System.currentTimeMillis();
            }
        }

        @Override
        public String changeSessionId() {
            final String oldId = sessionId;
            final InMemorySession sess = sessions.get(oldId);
            String newId = sessionIdGenerator.createSessionId();
            this.sessionId = newId;
            sessions.put(newId, sess);
            sessions.remove(oldId);
            return newId;
        }

    }

    /**
     * class that holds the real session data
     */
    private static class InMemorySession {

        final Session session;

        InMemorySession(final Session session, int maxInactiveInterval) {
            this.session = session;
            creationTime = lastAccessed = System.currentTimeMillis();
            this.maxInactiveInterval = maxInactiveInterval;
        }

        final ConcurrentMap<String, Object> attributes = new SecureHashMap<String, Object>();
        volatile long lastAccessed;
        final long creationTime;
        volatile int maxInactiveInterval;
    }
}
