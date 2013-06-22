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

package io.undertow.servlet.spec;

import java.util.Map;

import javax.servlet.SessionCookieConfig;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.session.SessionConfig;
import io.undertow.servlet.UndertowServletMessages;

/**
 * @author Stuart Douglas
 */
public class SessionCookieConfigImpl implements SessionCookieConfig, SessionConfig {

    public static final String DEFAULT_SESSION_ID = "JSESSIONID";

    private final ServletContextImpl servletContext;
    private String name = DEFAULT_SESSION_ID;
    private String path = "/";
    private String domain;
    private boolean secure;
    private boolean httpOnly;
    private int maxAge;
    private String comment;
    private SessionConfig fallback;

    public SessionCookieConfigImpl(final ServletContextImpl servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public String rewriteUrl(final String originalUrl, final String sessionid) {
        return originalUrl;
    }

    @Override
    public void setSessionId(final HttpServerExchange exchange, final String sessionId) {
        Cookie cookie = new CookieImpl(name, sessionId)
                .setPath(path)
                .setDomain(domain)
                .setSecure(secure)
                .setHttpOnly(httpOnly)
                .setComment(comment);
        if (maxAge > 0) {
            cookie.setMaxAge(maxAge);
        }
        exchange.setResponseCookie(cookie);
    }

    @Override
    public void clearSession(final HttpServerExchange exchange, final String sessionId) {
        Cookie cookie = new CookieImpl(name, sessionId)
                .setPath(path)
                .setDomain(domain)
                .setSecure(secure)
                .setHttpOnly(httpOnly)
                .setMaxAge(0);
        exchange.setResponseCookie(cookie);
    }

    @Override
    public String findSessionId(final HttpServerExchange exchange) {
        Map<String, Cookie> cookies = exchange.getRequestCookies();
        if (cookies != null) {
            Cookie sessionId = cookies.get(name);
            if (sessionId != null) {
                return sessionId.getValue();
            }
        }
        if(fallback != null) {
            return fallback.findSessionId(exchange);
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        if(servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.name = name;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(final String domain) {
        if(servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.domain = domain;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        if(servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.path = path;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        if(servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.comment = comment;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public void setHttpOnly(final boolean httpOnly) {
        if(servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.httpOnly = httpOnly;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(final boolean secure) {
        if(servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.secure = secure;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(final int maxAge) {
        if(servletContext.isInitialized()) {
            throw UndertowServletMessages.MESSAGES.servletContextAlreadyInitialized();
        }
        this.maxAge = maxAge;
    }

    public SessionConfig getFallback() {
        return fallback;
    }

    public void setFallback(final SessionConfig fallback) {
        this.fallback = fallback;
    }
}
