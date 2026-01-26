/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.undertow.server.session;

import io.undertow.server.HttpServerExchange;

/**
 * A {@link SessionConfig} that references a specific session.
 * @author Paul Ferraro
 */
public class SessionReferenceConfig implements SessionConfig {

    private final SessionReference reference;

    public SessionReferenceConfig(SessionReference reference) {
        this.reference = reference;
    }

    @Override
    public void setSessionId(HttpServerExchange exchange, String sessionId) {
    }

    @Override
    public void clearSession(HttpServerExchange exchange, String sessionId) {
    }

    @Override
    public String findSessionId(HttpServerExchange exchange) {
        return this.reference.getId();
    }

    @Override
    public SessionCookieSource sessionCookieSource(HttpServerExchange exchange) {
        return SessionCookieSource.NONE;
    }

    @Override
    public String rewriteUrl(String originalUrl, String sessionId) {
        return originalUrl;
    }
}
