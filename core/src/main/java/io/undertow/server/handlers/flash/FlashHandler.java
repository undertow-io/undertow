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

import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.server.session.SessionManager;

/**
 * Handler that sets up a Flash store (a way to transfer transient data that survives redirects, to the next request)
 * It uses the session to transfer the flash attachment to the next request. Once the handler is set up,
 * create and use an instance of {@link FlashManager} to work with the flash store.
 *
 * @author <a href="mailto:andrei.zinca@gmail.com">Andrei Zinca</a>
 */
public class FlashHandler implements HttpHandler {

    private static final String FLASH_SESSION_KEY = "_flash";

    private HttpHandler handler;
    private SessionConfig sessionConfig;

    public FlashHandler(HttpHandler handler, SessionConfig sessionConfig) {
        this.handler = handler;
        this.sessionConfig = sessionConfig;
    }

    private ExchangeCompletionListener completionListener = new ExchangeCompletionListener() {
        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {

            // Make sure our code is the last by running the rest of the listeners ahead of us
            nextListener.proceed();

            Session session = getOrCreateSession(exchange);

            // If the session flash attribute is available, it means we already served the attached flash
            // Remove the session attribute (the attachment does not need removal as it will not be available next)
            if (session.getAttribute(FLASH_SESSION_KEY) != null) {
                session.removeAttribute(FLASH_SESSION_KEY);
            } else {
                // The session was not set. If we have a flash attachment then it is meant to be
                // consumed in the next request, so transfer it using the session
                Object outgoingFlash = exchange.removeAttachment(FlashManager.FLASH_ATTACHMENT_KEY);
                if (outgoingFlash != null) {
                    session.setAttribute(FLASH_SESSION_KEY, outgoingFlash);
                }
            }
        }
    };

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        // Execute this listener right before the requests close, in order to extract
        // the flash attribute and pass it on to the next request via session
        exchange.addExchangeCompleteListener(completionListener);

        // Retrieve potential incoming flash from the session and attach it for the next request but
        // do not remove the session attribute yet, as it will be a way later in the completion listener
        // to tell if the flash was already transfered
        Object incomingFlash = getOrCreateSession(exchange).getAttribute(FLASH_SESSION_KEY);

        if (incomingFlash != null) {
            exchange.putAttachment(FlashManager.FLASH_ATTACHMENT_KEY, incomingFlash);
        }

        handler.handleRequest(exchange);
    }

    private Session getOrCreateSession(HttpServerExchange exchange) {
        SessionManager sessionManager = exchange.getAttachment(SessionManager.ATTACHMENT_KEY);
        Session session = sessionManager.getSession(exchange, sessionConfig);
        if (session != null) {
            return session;
        }
        return sessionManager.createSession(exchange, sessionConfig);
    }
}
