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
package io.undertow.security.handlers;

import io.undertow.UndertowLogger;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Handler responsible for checking the constraints for the current request and marking authentication as required if
 * applicable.
 *
 * Sub classes can override isAuthenticationRequired to provide a constraint check, by default this handler will set
 * authentication as always required, authentication will be optional if this handler is omitted.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class AuthenticationConstraintHandler implements HttpHandler {

    private final HttpHandler next;

    public AuthenticationConstraintHandler(final HttpHandler next) {
        this.next = next;
    }

    /**
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange)
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (isAuthenticationRequired(exchange)) {
            SecurityContext context = exchange.getSecurityContext();
            UndertowLogger.SECURITY_LOGGER.debugf("Setting authentication required for exchange %s", exchange);
            context.setAuthenticationRequired();
        }

        next.handleRequest(exchange);
    }

    /**
     * Evaluate the current request and indicate if authentication is required for the current request.
     *
     * By default this will always return true, sub-classes will override this method to provide a more specific check.
     *
     * @param exchange - the {@link HttpServerExchange} for the current request to decide if authentication is required.
     * @return true if authentication is required, false otherwise.
     */
    protected boolean isAuthenticationRequired(final HttpServerExchange exchange) {
        return true;
    }

}
