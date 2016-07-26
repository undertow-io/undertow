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
import io.undertow.server.HttpServerExchange;
import io.undertow.server.SSLSessionInfo;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Session config that stores the session ID in the current SSL session.
 * <p>
 * It allows for a fallback to be provided for non-ssl connections
 *
 * @author Stuart Douglas
 */
public class SslSessionConfig implements SessionConfig {

    private final SessionConfig fallbackSessionConfig;
    private final Map<Key, String> sessions = new HashMap<>();
    private final Map<String, Key> reverse = new HashMap<>();

    public SslSessionConfig(final SessionConfig fallbackSessionConfig, SessionManager sessionManager) {
        this.fallbackSessionConfig = fallbackSessionConfig;
        sessionManager.registerSessionListener(new SessionListener() {
            @Override
            public void sessionCreated(Session session, HttpServerExchange exchange) {
            }

            @Override
            public void sessionDestroyed(Session session, HttpServerExchange exchange, SessionDestroyedReason reason) {
                synchronized (SslSessionConfig.this) {
                    Key sid = reverse.remove(session.getId());
                    if (sid != null) {
                        sessions.remove(sid);
                    }
                }
            }

            @Override
            public void attributeAdded(Session session, String name, Object value) {
            }

            @Override
            public void attributeUpdated(Session session, String name, Object newValue, Object oldValue) {
            }

            @Override
            public void attributeRemoved(Session session, String name, Object oldValue) {
            }

            @Override
            public void sessionIdChanged(Session session, String oldSessionId) {
                synchronized (SslSessionConfig.this) {
                    Key sid = reverse.remove(session.getId());
                    if (sid != null) {
                        sessions.remove(sid);
                    }
                }
            }
        });
    }

    public SslSessionConfig(SessionManager sessionManager) {
        this(null, sessionManager);
    }

    @Override
    public void setSessionId(final HttpServerExchange exchange, final String sessionId) {
        UndertowLogger.SESSION_LOGGER.tracef("Setting SSL session id %s on %s", sessionId, exchange);
        SSLSessionInfo sslSession = exchange.getConnection().getSslSessionInfo();
        if (sslSession == null) {
            if (fallbackSessionConfig != null) {
                fallbackSessionConfig.setSessionId(exchange, sessionId);
            }
        } else {
            Key key = new Key(sslSession.getSessionId());
            synchronized (this) {
                sessions.put(key, sessionId);
                reverse.put(sessionId, key);
            }
        }
    }

    @Override
    public void clearSession(final HttpServerExchange exchange, final String sessionId) {
        UndertowLogger.SESSION_LOGGER.tracef("Clearing SSL session id %s on %s", sessionId, exchange);
        SSLSessionInfo sslSession = exchange.getConnection().getSslSessionInfo();
        if (sslSession == null) {
            if (fallbackSessionConfig != null) {
                fallbackSessionConfig.clearSession(exchange, sessionId);
            }
        } else {
            synchronized (this) {
                Key sid = reverse.remove(sessionId);
                if (sid != null) {
                    sessions.remove(sid);
                }
            }
        }
    }

    @Override
    public String findSessionId(final HttpServerExchange exchange) {
        SSLSessionInfo sslSession = exchange.getConnection().getSslSessionInfo();
        if (sslSession == null) {
            if (fallbackSessionConfig != null) {
                return fallbackSessionConfig.findSessionId(exchange);
            }
        } else {
            synchronized (this) {
                String sessionId = sessions.get(new Key(sslSession.getSessionId()));
                if(sessionId != null) {
                    UndertowLogger.SESSION_LOGGER.tracef("Found SSL session id %s on %s", sessionId, exchange);
                }
                return sessionId;
            }
        }
        return null;
    }

    @Override
    public SessionCookieSource sessionCookieSource(HttpServerExchange exchange) {
        return findSessionId(exchange) != null ? SessionCookieSource.SSL : SessionCookieSource.NONE;
    }

    @Override
    public String rewriteUrl(final String originalUrl, final String sessionId) {
        return originalUrl;
    }

    private static final class Key {
        private final byte[] id;

        private Key(byte[] id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Key key = (Key) o;

            if (!Arrays.equals(id, key.id)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return id != null ? Arrays.hashCode(id) : 0;
        }
    }
}
