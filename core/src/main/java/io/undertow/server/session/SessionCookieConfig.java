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

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * Encapsulation of session cookie configuration. This removes the need for the session manager to
 * know about cookie configuration.
 *
 * @author Stuart Douglas
 */
public class SessionCookieConfig {

    public static final String ATTACHMENT_KEY = "io.undertow.session.SessionCookie";

    public static final String DEFAULT_SESSION_ID = "JSESSIONID";

    private final String cookieName;
    private final String path;
    private final String domain;
    private final boolean discard;
    private final boolean secure;


    public SessionCookieConfig(final String cookieName, final String path, final String domain, final boolean discard, final boolean secure) {
        this.cookieName = cookieName;
        this.path = path;
        this.domain = domain;
        this.discard = discard;
        this.secure = secure;
    }


    public void setSessionCookie(final HttpServerExchange exchange, final Session session) {
        if(exchange.isResponseStarted()) {
            UndertowLogger.REQUEST_LOGGER.couldNotSendSessionCookieAsResponseAlreadyStarted();
            return;
        }
        final StringBuilder header = new StringBuilder(cookieName);
        header.append("=\"");
        header.append(session.getId());
        header.append("\"; Version=\"1\"; ");
        if(path != null) {
            header.append("Path=\"");
            header.append(path);
            header.append("\"; ");
        }
        if(domain != null) {
            header.append("Domain=\"");
            header.append(domain);
            header.append("\"; ");
        }
        if(discard) {
            header.append("Discard; ");
        }
        if(secure) {
            header.append("Secure; ");
        }
        header.append("Max-Age=\"");
        header.append(session.getMaxInactiveInterval());
        header.append("\"; ");
        exchange.getResponseHeaders().add(Headers.SET_COOKIE2, header.toString());

    }

    public void clearCookie(final HttpServerExchange exchange, final Session session) {
        if(exchange.isResponseStarted()) {
            UndertowLogger.REQUEST_LOGGER.couldNotInvalidateSessionCookieAsResponseAlreadyStarted();
            return;
        }
        final StringBuilder header = new StringBuilder(cookieName);
        header.append('=');
        header.append(session.getId());
        header.append("; ");
        if(path != null) {
            header.append("Path=");
            header.append(path);
            header.append("; ");
        }
        if(domain != null) {
            header.append("Domain=");
            header.append(domain);
            header.append("; ");
        }
        header.append("Max-Age=0");
        exchange.getResponseHeaders().add(Headers.SET_COOKIE2, header.toString());
    }
}
