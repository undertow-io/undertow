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

import java.util.Map;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;

/**
 * Encapsulation of session cookie configuration. This removes the need for the session manager to
 * know about cookie configuration.
 *
 * @author Stuart Douglas
 */
public class SessionCookieConfig implements SessionConfig {

    public static final String DEFAULT_SESSION_ID = "JSESSIONID";

    private String cookieName = DEFAULT_SESSION_ID;
    private String path = "/";
    private String domain;
    private boolean discard;
    private boolean secure;
    private boolean httpOnly;
    private int maxAge;
    private String comment;


    @Override
    public String rewriteUrl(final String originalUrl, final String sessionId) {
        return originalUrl;
    }

    @Override
    public void setSessionId(final HttpServerExchange exchange, final String sessionId) {
        Cookie cookie = new CookieImpl(cookieName, sessionId)
                .setPath(path)
                .setDomain(domain)
                .setDiscard(discard)
                .setSecure(secure)
                .setHttpOnly(httpOnly)
                .setComment(comment);
        if (maxAge > 0) {
            cookie.setMaxAge(maxAge);
        }
        exchange.setResponseCookie(cookie);
        UndertowLogger.SESSION_LOGGER.tracef("Setting session cookie session id %s on %s", sessionId, exchange);
    }

    @Override
    public void clearSession(final HttpServerExchange exchange, final String sessionId) {
        Cookie cookie = new CookieImpl(cookieName, sessionId)
                .setPath(path)
                .setDomain(domain)
                .setDiscard(discard)
                .setSecure(secure)
                .setHttpOnly(httpOnly)
                .setMaxAge(0);
        exchange.setResponseCookie(cookie);
        UndertowLogger.SESSION_LOGGER.tracef("Clearing session cookie session id %s on %s", sessionId, exchange);
    }

    @Override
    public String findSessionId(final HttpServerExchange exchange) {
        Map<String, Cookie> cookies = exchange.getRequestCookies();
        if (cookies != null) {
            Cookie sessionId = cookies.get(cookieName);
            if (sessionId != null) {
                UndertowLogger.SESSION_LOGGER.tracef("Found session cookie session id %s on %s", sessionId, exchange);
                return sessionId.getValue();
            }
        }
        return null;
    }

    @Override
    public SessionCookieSource sessionCookieSource(HttpServerExchange exchange) {
        return findSessionId(exchange) != null ? SessionCookieSource.COOKIE : SessionCookieSource.NONE;
    }

    public String getCookieName() {
        return cookieName;
    }

    public SessionCookieConfig setCookieName(final String cookieName) {
        this.cookieName = cookieName;
        return this;
    }

    public String getPath() {
        return path;
    }

    public SessionCookieConfig setPath(final String path) {
        this.path = path;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public SessionCookieConfig setDomain(final String domain) {
        this.domain = domain;
        return this;
    }

    public boolean isDiscard() {
        return discard;
    }

    public SessionCookieConfig setDiscard(final boolean discard) {
        this.discard = discard;
        return this;
    }

    public boolean isSecure() {
        return secure;
    }

    public SessionCookieConfig setSecure(final boolean secure) {
        this.secure = secure;
        return this;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public SessionCookieConfig setHttpOnly(final boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public SessionCookieConfig setMaxAge(final int maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public String getComment() {
        return comment;
    }

    public SessionCookieConfig setComment(final String comment) {
        this.comment = comment;
        return this;
    }
}
