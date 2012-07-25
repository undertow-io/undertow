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

package tmp.texugo.server.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;
import tmp.texugo.TexugoLogger;
import tmp.texugo.TexugoMessages;
import tmp.texugo.server.HttpServerExchange;
import tmp.texugo.util.SecureHashMap;

/**
 * The default in memory session manager. This basically just stores sessions in an in memory hash map.
 * <p/>
 * TODO: implement session expiration
 *
 * @author Stuart Douglas
 */
public class InMemorySessionManager implements SessionManager {

    private volatile SessionIdGenerator sessionIdGenerator = UUIDSessionIdGenerator.INSTANCE;

    private final ConcurrentMap<String, InMemorySession> sessions = new SecureHashMap<String, InMemorySession>();

    private volatile List<SessionListener> listeners = Collections.emptyList();

    /**
     * 30 minute default
     */
    private volatile int defaultSessionTimeout = 30 * 60;

    @Override
    public Session createSession(final HttpServerExchange serverExchange) {
        final String sessionID = sessionIdGenerator.createSessionId();
        final Session session = new SessionImpl(sessionID);
        InMemorySession im = new InMemorySession(session, defaultSessionTimeout);
        sessions.put(sessionID, im);
        for (SessionListener listener : listeners) {
            listener.sessionCreated(session, serverExchange);
        }
        final SessionCookieConfig config = (SessionCookieConfig) serverExchange.getAttachment(SessionCookieConfig.ATTACHMENT_KEY);
        if(config != null) {
            config.setSessionCookie(serverExchange, session);
        } else {
            TexugoLogger.REQUEST_LOGGER.couldNotFindSessionCookieConfig();
        }
        return session;
    }

    @Override
    public IoFuture<Session> createSessionAsync(final HttpServerExchange serverExchange) {
        return new FinishedIoFuture<Session>(createSession(serverExchange));
    }

    @Override
    public Session getSession(final HttpServerExchange serverExchange, final String sessionId) {
        final InMemorySession sess = sessions.get(sessionId);
        if (sess == null) {
            return null;
        } else {
            sess.lastAccessed = System.currentTimeMillis();
            return sess.session;
        }
    }

    @Override
    public IoFuture<Session> getSessionAsync(final HttpServerExchange serverExchange, final String sessionId) {
        return new FinishedIoFuture<Session>(getSession(serverExchange, sessionId));
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

        private final String sessionId;

        private SessionImpl(final String sessionId) {
            this.sessionId = sessionId;
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
                throw TexugoMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return sess.creationTime;
        }

        @Override
        public long getLastAccessedTime() {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw TexugoMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return sess.lastAccessed;
        }

        @Override
        public void setMaxInactiveInterval(final int interval) {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw TexugoMessages.MESSAGES.sessionNotFound(sessionId);
            }
            sess.maxInactiveInterval = interval;
        }

        @Override
        public int getMaxInactiveInterval() {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw TexugoMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return sess.maxInactiveInterval;
        }

        @Override
        public Object getAttribute(final String name) {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw TexugoMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return sess.attributes.get(name);
        }

        @Override
        public IoFuture<Object> getAttributeAsync(final String name) {
            return new FinishedIoFuture<Object>(getAttribute(name));
        }

        @Override
        public Set<String> getAttributeNames() {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw TexugoMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return sess.attributes.keySet();
        }

        @Override
        public IoFuture<Set<String>> getAttributeNamesAsync() {
            return new FinishedIoFuture<Set<String>>(getAttributeNames());
        }

        @Override
        public void setAttribute(final String name, final Object value) {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw TexugoMessages.MESSAGES.sessionNotFound(sessionId);
            }
            final Object existing = sess.attributes.put(name, value);
            for (SessionListener listener : listeners) {
                if (existing == null) {
                    listener.attributeAdded(sess.session, name, value);
                } else {
                    listener.attributeUpdated(sess.session, name, value);
                }
            }
        }

        @Override
        public IoFuture<Void> setAttributeAsync(final String name, final Object value) {
            setAttribute(name, value);
            return new FinishedIoFuture<Void>(null);
        }

        @Override
        public void removeAttribute(final String name) {
            final InMemorySession sess = sessions.get(sessionId);
            if (sess == null) {
                throw TexugoMessages.MESSAGES.sessionNotFound(sessionId);
            }
            final Object existing = sess.attributes.remove(name);
            for (SessionListener listener : listeners) {
                listener.attributeRemoved(sess.session, name);
            }
        }

        @Override
        public IoFuture<Void> removeAttributeAsync(final String name) {
            removeAttribute(name);
            return new FinishedIoFuture<Void>(null);
        }

        @Override
        public void invalidate(final HttpServerExchange exchange) {
            final InMemorySession sess = sessions.remove(sessionId);
            if (sess != null) {
                for (SessionListener listener : listeners) {
                    listener.sessionDestroyed(sess.session, exchange, false);
                }
            }
            final SessionCookieConfig config = (SessionCookieConfig) exchange.getAttachment(SessionCookieConfig.ATTACHMENT_KEY);
            if(config != null) {
                config.clearCookie(exchange, this);
            } else {
                TexugoLogger.REQUEST_LOGGER.couldNotFindSessionCookieConfig();
            }
        }

        @Override
        public IoFuture<Void> invalidateAsync(final HttpServerExchange exchange) {
            invalidate(exchange);
            return new FinishedIoFuture<Void>(null);
        }

        @Override
        public SessionManager getSessionManager() {
            return InMemorySessionManager.this;
        }

    }

    /**
     * class that holds the real session data
     */
    private static class InMemorySession {

        private final Session session;

        InMemorySession(final Session session, int maxInactiveInterval) {
            this.session = session;
            creationTime = lastAccessed = System.currentTimeMillis();
            this.maxInactiveInterval = maxInactiveInterval;
        }

        private final ConcurrentMap<String, Object> attributes = new SecureHashMap<String, Object>();
        private volatile long lastAccessed;
        private final long creationTime;
        private volatile int maxInactiveInterval;
    }
}
