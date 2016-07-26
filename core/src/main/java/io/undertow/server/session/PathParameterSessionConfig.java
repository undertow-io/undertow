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

import java.util.Deque;
import java.util.Locale;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;

/**
 * Session config that is based on a path parameter and URL rewriting
 *
 * @author Stuart Douglas
 */
public class PathParameterSessionConfig implements SessionConfig {

    private final String name;

    public PathParameterSessionConfig(final String name) {
        this.name = name;
    }

    public PathParameterSessionConfig() {
        this(SessionCookieConfig.DEFAULT_SESSION_ID.toLowerCase(Locale.ENGLISH));
    }

    @Override
    public void setSessionId(final HttpServerExchange exchange, final String sessionId) {
        exchange.getPathParameters().remove(name);
        exchange.addPathParam(name, sessionId);
        UndertowLogger.SESSION_LOGGER.tracef("Setting path parameter session id %s on %s", sessionId, exchange);
    }

    @Override
    public void clearSession(final HttpServerExchange exchange, final String sessionId) {
        UndertowLogger.SESSION_LOGGER.tracef("Clearing path parameter session id %s on %s", sessionId, exchange);
        exchange.getPathParameters().remove(name);
    }

    @Override
    public String findSessionId(final HttpServerExchange exchange) {
        Deque<String> stringDeque = exchange.getPathParameters().get(name);
        if (stringDeque == null) {
            return null;
        }
        UndertowLogger.SESSION_LOGGER.tracef("Found path parameter session id %s on %s", stringDeque.getFirst(), exchange);
        return stringDeque.getFirst();
    }

    @Override
    public SessionCookieSource sessionCookieSource(HttpServerExchange exchange) {
        return findSessionId(exchange) != null ? SessionCookieSource.URL : SessionCookieSource.NONE;
    }

    /**
     * Return the specified URL with the specified session identifier
     * suitably encoded.
     *
     * @param url       URL to be encoded with the session id
     * @param sessionId Session id to be included in the encoded URL
     */
    @Override
    public String rewriteUrl(final String url, final String sessionId) {
        if ((url == null) || (sessionId == null))
            return (url);

        String path = url;
        String query = "";
        String anchor = "";
        String fragment = "";
        int question = url.indexOf('?');
        if (question >= 0) {
            path = url.substring(0, question);
            query = url.substring(question);
        }
        int pound = path.indexOf('#');
        if (pound >= 0) {
            anchor = path.substring(pound);
            path = path.substring(0, pound);
        }
        int fragmentIndex = path.lastIndexOf(';');
        if(fragmentIndex >= 0) {
            fragment = path.substring(fragmentIndex);
            path = path.substring(0, fragmentIndex);
        }

        StringBuilder sb = new StringBuilder(path);
        if (sb.length() > 0) { // jsessionid can't be first.
            if(fragmentIndex > 0) {
                sb.append(fragment);
                sb.append("&");
            } else {
                sb.append(';');
            }
            sb.append(name.toLowerCase(Locale.ENGLISH));
            sb.append('=');
            sb.append(sessionId);
        }
        sb.append(anchor);
        sb.append(query);
        UndertowLogger.SESSION_LOGGER.tracef("Rewrote URL from %s to %s", url, sessionId);
        return (sb.toString());
    }
}
