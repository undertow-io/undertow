/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Base class responsible for associating the {@link SecurityContext} instance with the current request.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class AbstractSecurityContextAssociationHandler implements HttpHandler {

    private final HttpHandler next;

    protected AbstractSecurityContextAssociationHandler(final HttpHandler next) {
        this.next = next;
    }

    /**
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange)
     */
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        SecurityActions.setSecurityContext(exchange, createSecurityContext(exchange));
        next.handleRequest(exchange);
    }

    public abstract SecurityContext createSecurityContext(final HttpServerExchange exchange);

}
