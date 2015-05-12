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

import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * Interface that abstracts the process of attaching a session to an exchange. This includes both the HTTP side of
 * attachment such as setting a cookie, as well as actually attaching the session to the exchange for use by later
 * handlers.
 *
 * <p>
 * Generally this will just set a cookie.
 *
 * @author Stuart Douglas
 */
public interface SessionConfig {

    AttachmentKey<SessionConfig> ATTACHMENT_KEY = AttachmentKey.create(SessionConfig.class);

    /**
     * Attaches the session to the exchange. The method should attach the exchange under an attachment key,
     * and should also modify the exchange to allow the session to be re-attached on the next request.
     * <p>
     * Generally this will involve setting a cookie
     * <p>
     * Once a session has been attached it must be possible to retrieve it via
     * {@link #findSessionId(io.undertow.server.HttpServerExchange)}
     *
     *
     * @param exchange The exchange
     * @param sessionId  The session
     */
    void setSessionId(final HttpServerExchange exchange, final String sessionId);

    /**
     * Clears this session from the exchange, removing the attachment and making any changes to the response necessary,
     * such as clearing cookies.
     *
     * @param exchange The exchange
     * @param sessionId  The session id
     */
    void clearSession(final HttpServerExchange exchange, final String sessionId);

    /**
     * Retrieves a session id of an existing session from an exchange.
     *
     * @param exchange The exchange
     * @return The session id, or null
     */
    String findSessionId(final HttpServerExchange exchange);

    SessionCookieSource sessionCookieSource(final HttpServerExchange exchange);

    String rewriteUrl(final String originalUrl, final String sessionId);

    enum SessionCookieSource {
        URL,
        COOKIE,
        SSL,
        OTHER,
        NONE
    }

}
