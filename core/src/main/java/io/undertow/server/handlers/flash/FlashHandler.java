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

package io.undertow.server.handlers.flash;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;

/**
 * Handler that sets up a Flash store (a way to transfer transient data that survives redirects, to the next request)
 * It uses the {@link Session} to transfer the store to the next request so you have to make sure you have an active
 * session for this to work.
 *
 * @author <a href="mailto:andrei.zinca@gmail.com">Andrei Zinca</a>
 */
public class FlashHandler implements HttpHandler {

    protected static String FLASH_SESSION_KEY = "_flash";

    private HttpHandler next;
    private SessionConfig sessionConfig;
    private FlashStoreManager flashStoreManager;

    public FlashHandler(HttpHandler next, SessionConfig sessionConfig, FlashStoreManager flashStoreManager) {
        this.next = next;
        this.sessionConfig = sessionConfig;
        this.flashStoreManager = flashStoreManager;
    }

    public FlashHandler(SessionConfig sessionConfig, FlashStoreManager flashStoreManager) {
        this(null, sessionConfig, flashStoreManager);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        // First check for incoming flash store from the previous request and attach if the case
        Session session = getSession(exchange);
        if (session != null) {
            Object incomingFlashStore = session.removeAttribute(FLASH_SESSION_KEY);
            if (incomingFlashStore != null) {
                exchange.putAttachment(FlashStoreManager.ATTACHMENT_KEY_IN, incomingFlashStore);
            }
        }

        // No incoming flash. Initialize with empty store
        if (exchange.getAttachment(FlashStoreManager.ATTACHMENT_KEY_IN) == null) {
            exchange.putAttachment(FlashStoreManager.ATTACHMENT_KEY_IN, flashStoreManager.buildStore());
        }

        // Initialize the outgoing store
        exchange.putAttachment(FlashStoreManager.ATTACHMENT_KEY_OUT, flashStoreManager.buildStore());

        // Execute the next handlers
        next.handleRequest(exchange);

        // Transfer outgoing flash to the next request via session
        session = getSession(exchange);
        Object outgoingFlashStore = exchange.getAttachment(FlashStoreManager.ATTACHMENT_KEY_OUT);
        if (outgoingFlashStore != null && session != null) {
            session.setAttribute(FLASH_SESSION_KEY, outgoingFlashStore);
        }

    }

    private Session getSession(HttpServerExchange exchange) {
        SessionManager sessionManager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
        return sessionManager.getSession(exchange, sessionConfig);
    }

    public HttpHandler getNext() {
        return next;
    }

    public FlashHandler setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        this.next = next;
        return this;
    }
}
