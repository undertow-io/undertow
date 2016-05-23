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

package io.undertow.server.session;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConcurrentDirectDeque;

import java.math.BigDecimal;
import java.math.MathContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;

/**
 * The default in memory session manager. This basically just stores sessions in an in memory hash map.
 * <p>
 *
 * @author Stuart Douglas
 */
public class InMemorySessionManager implements SessionManager, SessionManagerStatistics {

    private final AttachmentKey<SessionImpl> NEW_SESSION = AttachmentKey.create(SessionImpl.class);

    private final SessionIdGenerator sessionIdGenerator;

    private final ConcurrentMap<String, SessionImpl> sessions;

    private final SessionListeners sessionListeners = new SessionListeners();

    /**
     * 30 minute default
     */
    private volatile int defaultSessionTimeout = 30 * 60;

    private final int maxSize;

    private final ConcurrentDirectDeque<String> evictionQueue;

    private final String deploymentName;

    private final AtomicLong createdSessionCount = new AtomicLong();
    private final AtomicLong expiredSessionCount = new AtomicLong();
    private final AtomicLong rejectedSessionCount = new AtomicLong();
    private final AtomicLong averageSessionLifetime = new AtomicLong();
    private final AtomicLong longestSessionLifetime = new AtomicLong();
    private final boolean statisticsEnabled;

    private volatile long startTime;

    private final boolean expireOldestUnusedSessionOnMax;


    public InMemorySessionManager(String deploymentName, int maxSessions, boolean expireOldestUnusedSessionOnMax) {
        this(new SecureRandomSessionIdGenerator(), deploymentName, maxSessions, expireOldestUnusedSessionOnMax);
    }

    public InMemorySessionManager(SessionIdGenerator sessionIdGenerator, String deploymentName, int maxSessions, boolean expireOldestUnusedSessionOnMax) {
        this(sessionIdGenerator, deploymentName, maxSessions, expireOldestUnusedSessionOnMax, true);
    }

    public InMemorySessionManager(SessionIdGenerator sessionIdGenerator, String deploymentName, int maxSessions, boolean expireOldestUnusedSessionOnMax, boolean statisticsEnabled) {
        this.sessionIdGenerator = sessionIdGenerator;
        this.deploymentName = deploymentName;
        this.statisticsEnabled = statisticsEnabled;
        this.expireOldestUnusedSessionOnMax = expireOldestUnusedSessionOnMax;
        this.sessions = new ConcurrentHashMap<>();
        this.maxSize = maxSessions;
        ConcurrentDirectDeque<String> evictionQueue = null;
        if (maxSessions > 0) {
            evictionQueue = ConcurrentDirectDeque.newInstance();
        }
        this.evictionQueue = evictionQueue;
    }

    public InMemorySessionManager(String deploymentName, int maxSessions) {
        this(deploymentName, maxSessions, false);
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
        createdSessionCount.set(0);
        expiredSessionCount.set(0);
        startTime = System.currentTimeMillis();
    }

    @Override
    public void stop() {
        for (Map.Entry<String, SessionImpl> session : sessions.entrySet()) {
            session.getValue().destroy();
            sessionListeners.sessionDestroyed(session.getValue(), null, SessionListener.SessionDestroyedReason.UNDEPLOY);
        }
        sessions.clear();
    }

    @Override
    public Session createSession(final HttpServerExchange serverExchange, final SessionConfig config) {
        if (evictionQueue != null) {
            if(expireOldestUnusedSessionOnMax) {
                while (sessions.size() >= maxSize && !evictionQueue.isEmpty()) {

                    String key = evictionQueue.poll();
                    UndertowLogger.REQUEST_LOGGER.debugf("Removing session %s as max size has been hit", key);
                    SessionImpl toRemove = sessions.get(key);
                    if (toRemove != null) {
                        toRemove.invalidate(null, SessionListener.SessionDestroyedReason.TIMEOUT); //todo: better reason
                    }
                }
            } else if(sessions.size() >= maxSize) {
                if(statisticsEnabled) {
                    rejectedSessionCount.incrementAndGet();
                }
                throw UndertowMessages.MESSAGES.tooManySessions(maxSize);
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
        if(statisticsEnabled) {
            createdSessionCount.incrementAndGet();
        }
        final SessionImpl session = new SessionImpl(this, sessionID, config, serverExchange.getIoThread(), serverExchange.getConnection().getWorker(), evictionToken, defaultSessionTimeout);
        sessions.put(sessionID, session);
        config.setSessionId(serverExchange, session.getId());
        session.lastAccessed = System.currentTimeMillis();
        session.bumpTimeout();
        sessionListeners.sessionCreated(session, serverExchange);
        serverExchange.putAttachment(NEW_SESSION, session);
        return session;
    }

    @Override
    public Session getSession(final HttpServerExchange serverExchange, final SessionConfig config) {
        if (serverExchange != null) {
            SessionImpl newSession = serverExchange.getAttachment(NEW_SESSION);
            if(newSession != null) {
                return newSession;
            }
        }
        String sessionId = config.findSessionId(serverExchange);
        return getSession(sessionId);
    }

    @Override
    public Session getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        final SessionImpl sess = sessions.get(sessionId);
        if (sess == null) {
            return null;
        } else {
            return sess;
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
        return new HashSet<>(sessions.keySet());
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
        return this.deploymentName;
    }

    @Override
    public SessionManagerStatistics getStatistics() {
        return this;
    }

    public long getCreatedSessionCount() {
        return createdSessionCount.get();
    }

    @Override
    public long getMaxActiveSessions() {
        return maxSize;
    }

    @Override
    public long getActiveSessionCount() {
        return sessions.size();
    }

    @Override
    public long getExpiredSessionCount() {
        return expiredSessionCount.get();
    }

    @Override
    public long getRejectedSessions() {
        return rejectedSessionCount.get();

    }

    @Override
    public long getMaxSessionAliveTime() {
        return longestSessionLifetime.get();
    }

    @Override
    public long getAverageSessionAliveTime() {
        return averageSessionLifetime.get();
    }

    @Override
    public long getStartTime() {
        return startTime;
    }


    /**
     * session implementation for the in memory session manager
     */
    private static class SessionImpl implements Session {


        final InMemorySessionManager sessionManager;
        final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();
        volatile long lastAccessed;
        final long creationTime;
        volatile int maxInactiveInterval;

        static volatile AtomicReferenceFieldUpdater<SessionImpl, Object> evictionTokenUpdater;
        static {
            //this is needed in case there is unprivileged code on the stack
            //it needs to delegate to the createTokenUpdater() method otherwise the creation will fail
            //as the inner class cannot access the member
            evictionTokenUpdater = AccessController.doPrivileged(new PrivilegedAction<AtomicReferenceFieldUpdater<SessionImpl, Object>>() {
                @Override
                public AtomicReferenceFieldUpdater<SessionImpl, Object> run() {
                    return createTokenUpdater();
                }
            });
        }

        private static AtomicReferenceFieldUpdater<SessionImpl, Object> createTokenUpdater() {
            return AtomicReferenceFieldUpdater.newUpdater(SessionImpl.class, Object.class, "evictionToken");
        }


        private String sessionId;
        private volatile Object evictionToken;
        private final SessionConfig sessionCookieConfig;
        private volatile long expireTime = -1;
        private volatile boolean invalid = false;
        private volatile boolean invalidationStarted = false;

        final XnioExecutor executor;
        final XnioWorker worker;

        XnioExecutor.Key timerCancelKey;

        Runnable cancelTask = new Runnable() {
            @Override
            public void run() {
                worker.execute(new Runnable() {
                    @Override
                    public void run() {
                        long currentTime = System.currentTimeMillis();
                        if(currentTime >= expireTime) {
                            invalidate(null, SessionListener.SessionDestroyedReason.TIMEOUT);
                        } else {
                            timerCancelKey = executor.executeAfter(cancelTask, expireTime - currentTime, TimeUnit.MILLISECONDS);
                        }
                    }
                });
            }
        };

        private SessionImpl(final InMemorySessionManager sessionManager, final String sessionId, final SessionConfig sessionCookieConfig, final XnioExecutor executor, final XnioWorker worker, final Object evictionToken, final int maxInactiveInterval) {
            this.sessionManager = sessionManager;
            this.sessionId = sessionId;
            this.sessionCookieConfig = sessionCookieConfig;
            this.executor = executor;
            this.worker = worker;
            this.evictionToken = evictionToken;
            creationTime = lastAccessed = System.currentTimeMillis();
            this.maxInactiveInterval = maxInactiveInterval;
        }

        synchronized void bumpTimeout() {
            if(invalidationStarted) {
                return;
            }

            final int maxInactiveInterval = getMaxInactiveInterval();
            if (maxInactiveInterval > 0) {
                long newExpireTime = System.currentTimeMillis() + (maxInactiveInterval * 1000L);
                if(timerCancelKey != null && (newExpireTime < expireTime)) {
                    // We have to re-schedule as the new maxInactiveInterval is lower than the old one
                    if (!timerCancelKey.remove()) {
                        return;
                    }
                    timerCancelKey = null;
                }
                expireTime = newExpireTime;
                if(timerCancelKey == null) {
                    //+500ms, to make sure that the time has actually expired
                    //we don't re-schedule every time, as it is expensive
                    //instead when it expires we check if the timeout has been bumped, and if so we re-schedule
                    timerCancelKey = executor.executeAfter(cancelTask, (maxInactiveInterval * 1000L) + 500L, TimeUnit.MILLISECONDS);
                }
            } else {
                expireTime = -1;
                if(timerCancelKey != null) {
                    timerCancelKey.remove();
                    timerCancelKey = null;
                }
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
            if (!invalid) {
                lastAccessed = System.currentTimeMillis();
            }
        }

        @Override
        public long getCreationTime() {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return creationTime;
        }

        @Override
        public long getLastAccessedTime() {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return lastAccessed;
        }

        @Override
        public void setMaxInactiveInterval(final int interval) {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            maxInactiveInterval = interval;
            bumpTimeout();
        }

        @Override
        public int getMaxInactiveInterval() {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            return maxInactiveInterval;
        }

        @Override
        public Object getAttribute(final String name) {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            bumpTimeout();
            return attributes.get(name);
        }

        @Override
        public Set<String> getAttributeNames() {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            bumpTimeout();
            return attributes.keySet();
        }

        @Override
        public Object setAttribute(final String name, final Object value) {
            if (value == null) {
                return removeAttribute(name);
            }
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            final Object existing = attributes.put(name, value);
            if (existing == null) {
                sessionManager.sessionListeners.attributeAdded(this, name, value);
            } else {
               sessionManager.sessionListeners.attributeUpdated(this, name, value, existing);
            }
            bumpTimeout();
            return existing;
        }

        @Override
        public Object removeAttribute(final String name) {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionNotFound(sessionId);
            }
            final Object existing = attributes.remove(name);
            sessionManager.sessionListeners.attributeRemoved(this, name, existing);
            bumpTimeout();
            return existing;
        }

        @Override
        public void invalidate(final HttpServerExchange exchange) {
            invalidate(exchange, SessionListener.SessionDestroyedReason.INVALIDATED);
            if(exchange != null) {
                exchange.removeAttachment(sessionManager.NEW_SESSION);
            }
        }

        void invalidate(final HttpServerExchange exchange, SessionListener.SessionDestroyedReason reason) {
            synchronized(SessionImpl.this) {
                if (timerCancelKey != null) {
                    timerCancelKey.remove();
                }
                SessionImpl sess = sessionManager.sessions.remove(sessionId);
                if (sess == null) {
                    if (reason == SessionListener.SessionDestroyedReason.INVALIDATED) {
                        throw UndertowMessages.MESSAGES.sessionAlreadyInvalidated();
                    }
                    return;
                }
                invalidationStarted = true;
            }

            sessionManager.sessionListeners.sessionDestroyed(this, exchange, reason);
            invalid = true;

            if(sessionManager.statisticsEnabled) {
                long avg, newAvg;
                do {
                    avg = sessionManager.averageSessionLifetime.get();
                    BigDecimal bd = new BigDecimal(avg);
                    bd.multiply(new BigDecimal(sessionManager.expiredSessionCount.get())).add(bd);
                    newAvg = bd.divide(new BigDecimal(sessionManager.expiredSessionCount.get() + 1), MathContext.DECIMAL64).longValue();
                } while (!sessionManager.averageSessionLifetime.compareAndSet(avg, newAvg));


                sessionManager.expiredSessionCount.incrementAndGet();
                long life = System.currentTimeMillis() - creationTime;
                long existing = sessionManager.longestSessionLifetime.get();
                while (life > existing) {
                    if (sessionManager.longestSessionLifetime.compareAndSet(existing, life)) {
                        break;
                    }
                    existing = sessionManager.longestSessionLifetime.get();
                }
            }
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
            String newId = sessionManager.sessionIdGenerator.createSessionId();
            this.sessionId = newId;
            if(!invalid) {
                sessionManager.sessions.put(newId, this);
                config.setSessionId(exchange, this.getId());
            }
            sessionManager.sessions.remove(oldId);
            sessionManager.sessionListeners.sessionIdChanged(this, oldId);
            return newId;
        }

        private synchronized void destroy() {
            if (timerCancelKey != null) {
                timerCancelKey.remove();
            }
            cancelTask = null;
        }

    }
}
