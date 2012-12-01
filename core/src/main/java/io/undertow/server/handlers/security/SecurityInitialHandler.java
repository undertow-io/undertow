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
package io.undertow.server.handlers.security;

import io.undertow.idm.IdentityManager;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.Session;

/**
 * The security handler responsible for attaching the SecurityContext to the current {@link HttpServerExchange}.
 *
 * This handler is called early in the processing of the incoming request, subsequently supported authentication mechanisms will
 * be added to the context, a decision will then be made if authentication is required or optional and the associated mechanisms
 * will be called.
 *
 * If any existing {@link SecurityContext} has been set it will be replaced and then restored as the the
 * {@link HttpCompletionHandler} is called, this allows for a general security configuration to be applied to a server and then
 * replaced with a context specific config.
 *
 * In addition to the HTTPExchange authentication state can also be associated with the {@link HttpServerConnection} and with
 * the {@link Session} however this is mechanism specific so it is down to the actual mechanisms to decide if there is state
 * that can be re-used.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SecurityInitialHandler implements HttpHandler {

    private final IdentityManager identityManager;
    private final HttpHandler next;

    public SecurityInitialHandler(final IdentityManager identityManager, final HttpHandler next) {
        this.identityManager = identityManager;
        this.next = next;
    }

    /**
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange,
     *      io.undertow.server.HttpCompletionHandler)
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
        SecurityContext existingContext = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        SecurityContext newContext = new SecurityContext(identityManager);
        exchange.putAttachment(SecurityContext.ATTACHMENT_KEY, newContext);

        HttpCompletionHandler wrapperHandler = new InitialCompletionHandler(exchange, existingContext, completionHandler);
        next.handleRequest(exchange, wrapperHandler);
    }

    private final class InitialCompletionHandler implements HttpCompletionHandler {

        private final HttpServerExchange exchange;
        private final SecurityContext originalSecurityContext;
        private final HttpCompletionHandler next;

        private InitialCompletionHandler(final HttpServerExchange exchange, final SecurityContext originalSecurityContext,
                final HttpCompletionHandler next) {
            this.exchange = exchange;
            this.originalSecurityContext = originalSecurityContext;
            this.next = next;
        }

        public void handleComplete() {
            exchange.putAttachment(SecurityContext.ATTACHMENT_KEY, originalSecurityContext);
            next.handleComplete();
        }

    }

}
