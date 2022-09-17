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
import io.undertow.util.WorkerUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.xnio.XnioExecutor;
import org.xnio.XnioIoThread;
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
    private final AtomicLong rejectedSessionCount = new AtomicLong();
    private volatile long longestSessionLifetime = 0;
    private volatile long expiredSessionCount = 0;
    private volatile BigInteger totalSessionLifetime = BigInteger.ZERO;
    private final AtomicInteger highestSessionCount = new AtomicInteger();

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
        if (maxSessions > 0 && expireOldestUnusedSessionOnMax) {
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
        expiredSessionCount = 0;
        rejectedSessionCount.set(0);
        totalSessionLifetime = BigInteger.ZERO;
        startTime = System.currentTimeMillis();
    }

    @Override
    public void stop() {
        for (Map.Entry<String, SessionImpl> session : sessions.entrySet()) {
            final SessionImpl sessionValue = session.getValue();
            sessionValue.destroy();
            if (sessionValue.getId() == null) {
                // this means we are creating the session right now in a different thread,
                // do not send the session to listener with a null id,
                // just set it again, setting the same session id twice is harmless
                sessionValue.setId(session.getKey());
            }
            sessionListeners.sessionDestroyed(session.getValue(), null, SessionListener.SessionDestroyedReason.UNDEPLOY);
        }
        sessions.clear();
    }

    @Override
    public Session createSession(final HttpServerExchange serverExchange, final SessionConfig config) {
        if (maxSize > 0) {
            if(expireOldestUnusedSessionOnMax) {
                while (sessions.size() >= maxSize && !evictionQueue.isEmpty()) {

                    String key = evictionQueue.poll();
                    if(key == null) {
                        break;
                    }
                    UndertowLogger.REQUEST_LOGGER.debugf("Removing session %s as max size has been hit", key);
                    SessionImpl toRemove = sessions.get(key);
                    if (toRemove != null) {
                        toRemove.invalidate(null, SessionListener.SessionDestroyedReason.TIMEOUT); //todo: better reason
                    }
                }
            } else if (sessions.size() >= maxSize) {
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
        final SessionImpl session = new SessionImpl(this, config, serverExchange.getIoThread(), serverExchange.getConnection().getWorker(), defaultSessionTimeout);
        if (sessionID != null) {
            if (!saveSessionID(sessionID, session))
                throw UndertowMessages.MESSAGES.sessionWithIdAlreadyExists(sessionID);
            // else: succeeded to use requested session id
        } else {
            sessionID = createAndSaveNewID(session);
        }
        session.setId(sessionID);
        if (evictionQueue != null) {
            session.setEvictionToken(evictionQueue.offerLastAndReturnToken(sessionID));
        }
        UndertowLogger.SESSION_LOGGER.debugf("Created session with id %s for exchange %s", sessionID, serverExchange);
        config.setSessionId(serverExchange, session.getId());
        session.bumpTimeout();
        sessionListeners.sessionCreated(session, serverExchange);
        serverExchange.putAttachment(NEW_SESSION, session);

        if(statisticsEnabled) {
            createdSessionCount.incrementAndGet();
            int highest;
            int sessionSize;
            do {
                highest = highestSessionCount.get();
                sessionSize = sessions.size();
                if(sessionSize <= highest) {
                    break;
                }
            } while (!highestSessionCount.compareAndSet(highest, sessionSize));
        }

        return session;
    }

    private boolean saveSessionID(String sessionID, SessionImpl session) {
        return this.sessions.putIfAbsent(sessionID, session) == null;
    }

    private String createAndSaveNewID(SessionImpl session) {
        for (int i = 0; i < 100; i++) {
            final String sessionID = sessionIdGenerator.createSessionId();
            if (saveSessionID(sessionID, session))
                return sessionID;
        }
        //this should 'never' happen
        //but we guard against pathological session id generators to prevent an infinite loop
        throw UndertowMessages.MESSAGES.couldNotGenerateUniqueSessionId();
    }

    @Override
    public Session getSession(final HttpServerExchange serverExchange, final SessionConfig config) {
        if (serverExchange != null) {
            SessionImpl newSession = serverExchange.getAttachment(NEW_SESSION);
            if(newSession != null) {
                return newSession;
            }
        } else {
            return null;
        }
        String sessionId = config.findSessionId(serverExchange);
        InMemorySessionManager.SessionImpl session = (SessionImpl) getSession(sessionId);
        if(session != null && serverExchange != null) {
            session.requestStarted(serverExchange);
        }
        return session;
    }

    @Override
    public Session getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        final SessionImpl sess = sessions.get(sessionId);
        if (sess == null) {
            return null;
        }
        if (sess.getId() == null) {
            // this means we are creating the session right now in a different thread,
            // do not return the session with a null id to the outer world,
            // just set it again, setting the same session id twice is harmless
            sess.setId(sessionId);
        }
        return sess;
    }

    @Override
    public synchronized void registerSessionListener(final SessionListener listener) {
        UndertowLogger.SESSION_LOGGER.debugf("Registered session listener %s", listener);
        sessionListeners.addSessionListener(listener);
    }

    @Override
    public synchronized void removeSessionListener(final SessionListener listener) {
        UndertowLogger.SESSION_LOGGER.debugf("Removed session listener %s", listener);
        sessionListeners.removeSessionListener(listener);
    }

    @Override
    public void setDefaultSessionTimeout(final int timeout) {
        UndertowLogger.SESSION_LOGGER.debugf("Setting default session timeout to %s", timeout);
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
    public long getHighestSessionCount() {
        return highestSessionCount.get();
    }

    @Override
    public long getActiveSessionCount() {
        return sessions.size();
    }

    @Override
    public long getExpiredSessionCount() {
        return expiredSessionCount;
    }

    @Override
    public long getRejectedSessions() {
        return rejectedSessionCount.get();

    }

    @Override
    public long getMaxSessionAliveTime() {
        return longestSessionLifetime;
    }

    @Override
    public synchronized long getAverageSessionAliveTime() {
        //this method needs to be synchronised to make sure the session count and the total are in sync
        if(expiredSessionCount == 0) {
            return 0;
        }
        return new BigDecimal(totalSessionLifetime).divide(BigDecimal.valueOf(expiredSessionCount), MathContext.DECIMAL128).longValue();
    }

    @Override
    public long getStartTime() {
        return startTime;
    }


    /**
     * session implementation for the in memory session manager
     */
    private static class SessionImpl implements Session {


        final AttachmentKey<Long> FIRST_REQUEST_ACCESS = AttachmentKey.create(Long.class);
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


        private volatile String sessionId;
        private volatile Object evictionToken;
        private final SessionConfig sessionCookieConfig;
        private volatile long expireTime = -1;
        private volatile boolean invalid = false;
        private volatile boolean invalidationStarted = false;

        final XnioIoThread executor;
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
                            timerCancelKey = WorkerUtils.executeAfter(executor, cancelTask, expireTime - currentTime, TimeUnit.MILLISECONDS);
                        }
                    }
                });
            }
        };

        private SessionImpl(final InMemorySessionManager sessionManager, final SessionConfig sessionCookieConfig, final XnioIoThread executor, final XnioWorker worker, final int maxInactiveInterval) {
            this.sessionManager = sessionManager;
            this.sessionCookieConfig = sessionCookieConfig;
            this.executor = executor;
            this.worker = worker;
            creationTime = lastAccessed = System.currentTimeMillis();
            this.setMaxInactiveInterval(maxInactiveInterval);
        }

        synchronized void bumpTimeout() {
            if(invalidationStarted) {
                return;
            }

            final long maxInactiveInterval = getMaxInactiveIntervalMilis();
            if (maxInactiveInterval > 0) {
                long newExpireTime = System.currentTimeMillis() + maxInactiveInterval;
                if(timerCancelKey != null && (newExpireTime < expireTime)) {
                    // We have to re-schedule as the new maxInactiveInterval is lower than the old one
                    if (!timerCancelKey.remove()) {
                        return;
                    }
                    timerCancelKey = null;
                }
                expireTime = newExpireTime;
                UndertowLogger.SESSION_LOGGER.tracef("Bumping timeout for session %s to %s", sessionId, expireTime);
                if(timerCancelKey == null) {
                    //+1, to make sure that the time has actually expired
                    //we don't re-schedule every time, as it is expensive
                    //instead when it expires we check if the timeout has been bumped, and if so we re-schedule
                    timerCancelKey = executor.executeAfter(cancelTask, maxInactiveInterval + 1L, TimeUnit.MILLISECONDS);
                }
            } else {
                expireTime = -1;
                if(timerCancelKey != null) {
                    timerCancelKey.remove();
                    timerCancelKey = null;
                }
            }
        }

        private void setEvictionToken(Object evictionToken) {
            this.evictionToken = evictionToken;
            if (evictionToken != null) {
                Object token = evictionToken;
                if (evictionTokenUpdater.compareAndSet(this, token, null)) {
                    sessionManager.evictionQueue.removeToken(token);
                    this.evictionToken = sessionManager.evictionQueue.offerLastAndReturnToken(sessionId);
                }
            }
        }

        private void setId(final String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getId() {
            return sessionId;
        }

        void requestStarted(HttpServerExchange serverExchange) {
            Long existing = serverExchange.getAttachment(FIRST_REQUEST_ACCESS);
            if(existing == null) {
                if (!invalid) {
                    serverExchange.putAttachment(FIRST_REQUEST_ACCESS, System.currentTimeMillis());
                }
            }
            bumpTimeout();
        }

        @Override
        public void requestDone(final HttpServerExchange serverExchange) {
            Long existing = serverExchange.getAttachment(FIRST_REQUEST_ACCESS);
            if(existing != null) {
                lastAccessed = existing;
            }
            bumpTimeout();
        }

        @Override
        public long getCreationTime() {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionIsInvalid(sessionId);
            }
            return creationTime;
        }

        @Override
        public long getLastAccessedTime() {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionIsInvalid(sessionId);
            }
            return lastAccessed;
        }

        @Override
        public void setMaxInactiveInterval(final int interval) {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionIsInvalid(sessionId);
            }
            UndertowLogger.SESSION_LOGGER.debugf("Setting max inactive interval for %s to %s", sessionId, interval);
            this.maxInactiveInterval = interval;
            this.bumpTimeout();
        }

        @Override
        public int getMaxInactiveInterval() {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionIsInvalid(sessionId);
            }
            return maxInactiveInterval;
        }

        private long getMaxInactiveIntervalMilis() {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionIsInvalid(sessionId);
            }
            return this.maxInactiveInterval*1000L;
        }

        @Override
        public Object getAttribute(final String name) {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionIsInvalid(sessionId);
            }
            return attributes.get(name);
        }

        @Override
        public Set<String> getAttributeNames() {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionIsInvalid(sessionId);
            }
            return attributes.keySet();
        }

        @Override
        public Object setAttribute(final String name, final Object value) {
            if (value == null) {
                return removeAttribute(name);
            }
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionIsInvalid(sessionId);
            }
            final Object existing = attributes.put(name, value);
            if (existing == null) {
                sessionManager.sessionListeners.attributeAdded(this, name, value);
            } else {
               sessionManager.sessionListeners.attributeUpdated(this, name, value, existing);
            }
            UndertowLogger.SESSION_LOGGER.tracef("Setting session attribute %s to %s for session %s", name, value, sessionId);
            return existing;
        }

        @Override
        public Object removeAttribute(final String name) {
            if (invalid) {
                throw UndertowMessages.MESSAGES.sessionIsInvalid(sessionId);
            }
            final Object existing = attributes.remove(name);
            sessionManager.sessionListeners.attributeRemoved(this, name, existing);
            UndertowLogger.SESSION_LOGGER.tracef("Removing session attribute %s for session %s", name, sessionId);
            return existing;
        }

        @Override
        public void invalidate(final HttpServerExchange exchange) {
            invalidate(exchange, SessionListener.SessionDestroyedReason.INVALIDATED);
            if (exchange != null) {
                exchange.removeAttachment(sessionManager.NEW_SESSION);
            }
            Object evictionToken = this.evictionToken;
            if(evictionToken != null) {
                sessionManager.evictionQueue.removeToken(evictionToken);
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
            UndertowLogger.SESSION_LOGGER.debugf("Invalidating session %s for exchange %s", sessionId, exchange);

            sessionManager.sessionListeners.sessionDestroyed(this, exchange, reason);
            invalid = true;

            if(sessionManager.statisticsEnabled) {
                long life = System.currentTimeMillis() - creationTime;
                synchronized (sessionManager) {
                    sessionManager.expiredSessionCount++;
                    sessionManager.totalSessionLifetime = sessionManager.totalSessionLifetime.add(BigInteger.valueOf(life));
                    if(sessionManager.longestSessionLifetime < life) {
                        sessionManager.longestSessionLifetime = life;
                    }
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
            synchronized(SessionImpl.this) {
                if (invalidationStarted) {
                    return null;
                } else {
                    final String oldId = sessionId;
                    String newId = sessionManager.createAndSaveNewID(this);
                    this.sessionId = newId;
                    config.setSessionId(exchange, this.getId());
                    sessionManager.sessions.remove(oldId);
                    sessionManager.sessionListeners.sessionIdChanged(this, oldId);
                    UndertowLogger.SESSION_LOGGER.debugf("Changing session id %s to %s", oldId, newId);

                    return newId;
                }
            }
        }

        private synchronized void destroy() {
            if (timerCancelKey != null) {
                timerCancelKey.remove();
            }
            cancelTask = null;
        }

    }
}
