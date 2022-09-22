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
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;

/**
 * Encapsulation of session cookie configuration. This removes the need for the session manager to
 * know about cookie configuration.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class SessionCookieConfig extends CookieAttributes<SessionCookieConfig> implements SessionConfig {

    public static final String DEFAULT_SESSION_ID = "JSESSIONID";
    public static final String DEFAULT_PATH = "/";
    private String cookieName = DEFAULT_SESSION_ID;

    @Override
    public String rewriteUrl(final String originalUrl, final String sessionId) {
        return originalUrl;
    }

    public SessionCookieConfig() {
        super();
        //NOTE some client dont consider lack of path as "/"...
        super.kernel.setPath(DEFAULT_PATH);
    }

    @Override
    public void setSessionId(final HttpServerExchange exchange, final String sessionId) {

        Cookie cookie = new CookieImpl(cookieName, sessionId, super.kernel);

        exchange.setResponseCookie(cookie);
        UndertowLogger.SESSION_LOGGER.tracef("Setting session cookie session id %s on %s", sessionId, exchange);
    }

    @Override
    public void clearSession(final HttpServerExchange exchange, final String sessionId) {
        Cookie cookie = new CookieImpl(cookieName, sessionId, super.kernel)
                .setMaxAge(0);
        exchange.setResponseCookie(cookie);
        UndertowLogger.SESSION_LOGGER.tracef("Clearing session cookie session id %s on %s", sessionId, exchange);
    }

    @Override
    public String findSessionId(final HttpServerExchange exchange) {
        final Cookie cookie = exchange.getRequestCookie(cookieName, getPath());
        if (cookie != null) {
            UndertowLogger.SESSION_LOGGER.tracef("Found session cookie session id %s on %s", cookie, exchange);
            return cookie.getValue();
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
}
