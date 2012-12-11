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

import javax.net.ssl.SSLSession;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * Session config that stores the session ID in the current SSL session.
 * <p/>
 * It allows for a fallback to be provided for non-ssl connections
 *
 * @author Stuart Douglas
 */
public class SslSessionConfig implements SessionConfig {

    private final SessionConfig fallbackSessionConfig;
    private final AttachmentKey<Session> attachmentKey;

    public SslSessionConfig(final SessionConfig fallbackSessionConfig, final AttachmentKey<Session> attachmentKey) {
        this.fallbackSessionConfig = fallbackSessionConfig;
        this.attachmentKey = attachmentKey;
    }

    public SslSessionConfig(final SessionConfig fallbackSessionConfig) {
        this(fallbackSessionConfig, AttachmentKey.create(Session.class));
    }

    public SslSessionConfig(final AttachmentKey<Session> attachmentKey) {
        this(null, attachmentKey);
    }

    public SslSessionConfig() {
        this(null, AttachmentKey.create(Session.class));
    }

    @Override
    public void attachSession(final HttpServerExchange exchange, final Session session) {
        exchange.putAttachment(attachmentKey, session);
        SSLSession sslSession = exchange.getConnection().getSslSession();
        if (sslSession == null) {
            if (fallbackSessionConfig != null) {
                fallbackSessionConfig.attachSession(exchange, session);
            }
        } else {
            sslSession.putValue(SslSessionConfig.class.getName(), session.getId());
        }
    }

    @Override
    public void clearSession(final HttpServerExchange exchange, final Session session) {
        SSLSession sslSession = exchange.getConnection().getSslSession();
        if (sslSession == null) {
            if (fallbackSessionConfig != null) {
                fallbackSessionConfig.clearSession(exchange, session);
            }
        } else {
            sslSession.putValue(SslSessionConfig.class.getName(), null);
        }
    }

    @Override
    public Session getAttachedSession(final HttpServerExchange exchange) {
        return exchange.getAttachment(attachmentKey);
    }

    @Override
    public String findSessionId(final HttpServerExchange exchange) {
        SSLSession sslSession = exchange.getConnection().getSslSession();
        if (sslSession == null) {
            if (fallbackSessionConfig != null) {
                return fallbackSessionConfig.findSessionId(exchange);
            }
        } else {
            return (String) sslSession.getValue(SslSessionConfig.class.getName());
        }
        return null;
    }

    @Override
    public String rewriteUrl(final String originalUrl, final Session session) {
        return originalUrl;
    }
}
