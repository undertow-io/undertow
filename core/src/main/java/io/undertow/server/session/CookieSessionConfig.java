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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.server.session;

import java.util.Map;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;

/**
 * Cookie-based {@link SessionConfig} implementation.
 * @author Paul Ferraro
 */
public class CookieSessionConfig implements SessionConfig {

    private final SessionCookieConfig config;
    private final SessionConfig fallbackSessionConfig;

    public CookieSessionConfig() {
        this(new SessionCookieConfig(), null);
    }

    public CookieSessionConfig(SessionCookieConfig config) {
        this(config, null);
    }

    public CookieSessionConfig(SessionCookieConfig config, SessionConfig fallback) {
        this.config = config;
        this.fallbackSessionConfig = fallback;
    }

    @Override
    public void setSessionId(HttpServerExchange exchange, String sessionId) {
        Cookie cookie = new CookieImpl(this.config.getCookieName(), sessionId)
                .setPath(this.config.getPath())
                .setDomain(this.config.getDomain())
                .setDiscard(this.config.isDiscard())
                .setSecure(this.config.isSecure())
                .setHttpOnly(this.config.isHttpOnly())
                .setComment(this.config.getComment());
        if (this.config.getMaxAge() > 0) {
            cookie.setMaxAge(this.config.getMaxAge());
        }
        exchange.setResponseCookie(cookie);
    }

    @Override
    public void clearSession(HttpServerExchange exchange, String sessionId) {
        Cookie cookie = new CookieImpl(this.config.getCookieName(), sessionId)
                .setPath(this.config.getPath())
                .setDomain(this.config.getDomain())
                .setDiscard(this.config.isDiscard())
                .setSecure(this.config.isSecure())
                .setHttpOnly(this.config.isHttpOnly())
                .setMaxAge(0);
        exchange.setResponseCookie(cookie);
    }

    @Override
    public String findSessionId(HttpServerExchange exchange) {
        Map<String, Cookie> cookies = exchange.getRequestCookies();
        if (cookies != null) {
            Cookie sessionId = cookies.get(this.config.getCookieName());
            if (sessionId != null) {
                return sessionId.getValue();
            }
        }
        return (this.fallbackSessionConfig != null) ? this.fallbackSessionConfig.findSessionId(exchange) : null;
    }

    @Override
    public SessionCookieSource sessionCookieSource(HttpServerExchange exchange) {
        Map<String, Cookie> cookies = exchange.getRequestCookies();
        if (cookies != null) {
            Cookie sessionId = cookies.get(this.config.getCookieName());
            if (sessionId != null) {
                return SessionCookieSource.COOKIE;
            }
        }
        return (this.fallbackSessionConfig != null) ? this.fallbackSessionConfig.sessionCookieSource(exchange) : SessionCookieSource.NONE;
    }

    @Override
    public String rewriteUrl(String originalUrl, String sessionId) {
        return originalUrl;
    }
}
