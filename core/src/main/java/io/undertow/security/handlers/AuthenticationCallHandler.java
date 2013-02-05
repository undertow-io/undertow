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
package io.undertow.security.handlers;

import java.io.IOException;

import org.xnio.IoFuture;
import org.xnio.IoFuture.Notifier;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;

/**
 * This is the final {@link HttpHandler} in the security chain, it's purpose is to act as a barrier at the end of the chain to
 * ensure authenticate is called after the mechanisms have been associated with the context and the constraint checked.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class AuthenticationCallHandler implements HttpHandler {

    private final HttpHandler next;

    public AuthenticationCallHandler(final HttpHandler next) {
        this.next = next;
    }

    /**
     * Only allow the request through if successfully authenticated or if authentication is not required.
     *
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange)
     */
    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        SecurityContext context = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        IoFuture<Boolean> result = context.authenticate();
        result.addNotifier(new Notifier<Boolean, Object>() {

            @Override
            public void notify(IoFuture<? extends Boolean> ioFuture, Object attachment) {
                try {
                    if (ioFuture.get()) {
                        // Response has already been send - end the exchange.
                        exchange.endExchange();
                    } else {
                        HttpHandlers.executeHandler(next, exchange);
                    }
                } catch (IOException e) {
                    // Response has already been send - end the exchange.
                    // TODO - Error reporting.
                    exchange.endExchange();
                }
            }
        }, null);

    }

}
