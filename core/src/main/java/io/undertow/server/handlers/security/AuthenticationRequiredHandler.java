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

import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * This is the final {@link HttpHandler} in the security chain, it's purpose is to act as a barrier at the end of the chain to
 * only allow the request through either if authentication was not required or it it was required to ensure that it was a
 * success.
 *
 * There is no special challenge generation within this handler, instead if the authentication state is not correct the call is
 * not passed to the next handler and instead returned back using the supplied {@link HttpCompletionHandler}
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class AuthenticationRequiredHandler implements HttpHandler {

    private final HttpHandler next;

    public AuthenticationRequiredHandler(final HttpHandler next) {
        this.next = next;
    }

    /**
     * Only allow the request through if successfully authenticated or if authentication is not required.
     *
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange,
     *      io.undertow.server.HttpCompletionHandler)
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
        SecurityContext context = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        context.authenticate(exchange, completionHandler, next);
    }

}
