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
        final int question = url.indexOf('?');
        if (question >= 0) {
            path = url.substring(0, question);
            query = url.substring(question);
        }
        final int pound = path.indexOf('#');
        if (pound >= 0) {
            anchor = path.substring(pound);
            path = path.substring(0, pound);
        }
        final StringBuilder sb = new StringBuilder();
        // look for param
        final int paramIndex = path.indexOf(";" + name);
        // found param, strip it off from path
        if (paramIndex >= 0) {
            sb.append(path.substring(0, paramIndex));
            final String remainder = path.substring(paramIndex + name.length() + 1);
            final int endIndex1 = remainder.indexOf(";");
            final int endIndex2 = remainder.indexOf("/");
            if (endIndex1 != -1) {
                if (endIndex2 != -1 && endIndex2 < endIndex1) {
                    sb.append(remainder.substring(endIndex2));
                } else {
                    sb.append(remainder.substring(endIndex1));
                }
            } else if (endIndex2 != -1) {
                sb.append(remainder.substring(endIndex2));
            }
            // else the rest of the path will be discarded, as it contains just the parameter we want to exclude
        } else {
            // name param was not found, we can use the path as is
            sb.append(path);
        }
        // append ;name=sessionId
        sb.append(';');
        sb.append(name);
        sb.append('=');
        sb.append(sessionId);
        // apend anchor and query
        sb.append(anchor);
        sb.append(query);
        UndertowLogger.SESSION_LOGGER.tracef("Rewrote URL from %s to %s", url, sb);
        return sb.toString();
    }
}
