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

package io.undertow.servlet.spec;

import java.util.Map;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.SessionConfig;
import io.undertow.servlet.UndertowServletMessages;
import jakarta.servlet.SessionCookieConfig;

/**
 * @author Stuart Douglas
 */
public class SessionCookieConfigImpl implements SessionCookieConfig, SessionConfig {

    private final ServletContextImpl servletContext;
    private final io.undertow.server.session.SessionCookieConfig delegate;
    private SessionConfig fallback;

    public SessionCookieConfigImpl(final ServletContextImpl servletContext) {
        this.servletContext = servletContext;
        this.delegate = new io.undertow.server.session.SessionCookieConfig();
    }

    @Override
    public String rewriteUrl(final String originalUrl, final String sessionid) {
        if (fallback != null) {
            return fallback.rewriteUrl(originalUrl, sessionid);
        }
        return originalUrl;
    }

    @Override
    public void setSessionId(final HttpServerExchange exchange, final String sessionId) {
        delegate.setSessionId(exchange, sessionId);
    }

    @Override
    public void clearSession(final HttpServerExchange exchange, final String sessionId) {
        delegate.clearSession(exchange, sessionId);
    }

    @Override
    public String findSessionId(final HttpServerExchange exchange) {
        String existing = delegate.findSessionId(exchange);
        if (existing != null) {
            return existing;
        }
        if (fallback != null) {
            return fallback.findSessionId(exchange);
        }
        return null;
    }

    @Override
    public SessionCookieSource sessionCookieSource(HttpServerExchange exchange) {
        String existing = delegate.findSessionId(exchange);
        if (existing != null) {
            return SessionCookieSource.COOKIE;
        }
        if (fallback != null) {
            String id = fallback.findSessionId(exchange);
            return id != null ? fallback.sessionCookieSource(exchange) : SessionCookieSource.NONE;
        }
        return SessionCookieSource.NONE;
    }

    public String getName() {
        return delegate.getCookieName();
    }

    public void setName(final String name) {
        if (servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        delegate.setCookieName(name);
    }

    public String getDomain() {
        return delegate.getDomain();
    }

    public void setDomain(final String domain) {
        if (servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        delegate.setDomain(domain);
    }

    public String getPath() {
        return delegate.getPath();
    }

    public void setPath(final String path) {
        if (servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        delegate.setPath(path);
    }

    @Deprecated
    public String getComment() {
        return delegate.getComment();
    }

    @Deprecated
    public void setComment(final String comment) {
        if (servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        delegate.setComment(comment);
    }

    public boolean isHttpOnly() {
        return delegate.isHttpOnly();
    }

    public void setHttpOnly(final boolean httpOnly) {
        if (servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        delegate.setHttpOnly(httpOnly);
    }

    public boolean isSecure() {
        return delegate.isSecure();
    }

    public void setSecure(final boolean secure) {
        if (servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        delegate.setSecure(secure);
    }

    public int getMaxAge() {
        return delegate.getMaxAge();
    }

    public void setMaxAge(final int maxAge) {
        if (servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.delegate.setMaxAge(maxAge);
    }

    public SessionConfig getFallback() {
        return fallback;
    }

    public void setFallback(final SessionConfig fallback) {
        this.fallback = fallback;
    }

    @Override
    public void setAttribute(final String name, final String value) {
        if (servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        delegate.setAttribute(name, value);
    }

    @Override
    public String getAttribute(final String name) {
        return delegate.getAttribute(name);
    }

    @Override
    public Map<String, String> getAttributes() {
        return delegate.getAttributes();
    }
}
