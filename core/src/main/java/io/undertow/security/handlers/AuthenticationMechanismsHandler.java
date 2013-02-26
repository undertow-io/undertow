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

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.server.handlers.ResponseCodeHandler;

import java.util.List;

/**
 * Authentication handler that adds one or more authentication
 * mechanisms to the security context
 *
 * @author Stuart Douglas
 */
public class AuthenticationMechanismsHandler implements HttpHandler {

    private volatile HttpHandler next = ResponseCodeHandler.HANDLE_404;
    private final List<AuthenticationMechanism> authenticationMechanisms;

    public AuthenticationMechanismsHandler(final HttpHandler next, final List<AuthenticationMechanism> authenticationMechanisms) {
        this.next = next;
        this.authenticationMechanisms = authenticationMechanisms;
    }

    public AuthenticationMechanismsHandler(final List<AuthenticationMechanism> authenticationHandlers) {
        this.authenticationMechanisms = authenticationHandlers;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) {
        final SecurityContext sc = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        if(sc != null) {
            for(AuthenticationMechanism mechanism : authenticationMechanisms) {
                sc.addAuthenticationMechanism(mechanism);
            }
        }
        HttpHandlers.executeHandler(next, exchange);
    }

    public HttpHandler getNext() {
        return next;
    }

    public void setNext(final HttpHandler next) {
        HttpHandlers.handlerNotNull(next);
        this.next = next;
    }

}
