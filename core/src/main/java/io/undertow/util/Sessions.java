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

package io.undertow.util;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;

/**
 * Utility class for working with sessions.
 *
 * @author Stuart Douglas
 */
public class Sessions {

    /**
     * Gets the active session, returning null if one is not present.
     * @param exchange The exchange
     * @return The session
     */
    public static Session getSession(final HttpServerExchange exchange) {
        return getSession(exchange, false);
    }

    /**
     * Gets the active session, creating a new one if one does not exist
     * @param exchange The exchange
     * @return The session
     */
    public static Session getOrCreateSession(final HttpServerExchange exchange) {
        return getSession(exchange, true);
    }

    private static Session getSession(final HttpServerExchange exchange, boolean create) {
        SessionManager sessionManager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
        SessionConfig sessionConfig = exchange.getAttachment(SessionConfig.ATTACHMENT_KEY);
        if(sessionManager == null) {
            throw UndertowMessages.MESSAGES.sessionManagerNotFound();
        }
        Session session = sessionManager.getSession(exchange, sessionConfig);
        if(session == null && create) {
            session = sessionManager.createSession(exchange, sessionConfig);
        }
        return session;
    }

    private Sessions () {}

}
