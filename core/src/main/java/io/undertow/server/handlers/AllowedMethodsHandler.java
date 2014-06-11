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

package io.undertow.server.handlers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

/**
 * Handler that whitelists certain HTTP methods. Only requests with a method in
 * the allowed methods set will be allowed to continue.
 *
 * @author Stuart Douglas
 */
public class AllowedMethodsHandler implements HttpHandler {

    private final Set<HttpString> allowedMethods;
    private final HttpHandler next;

    public AllowedMethodsHandler(final HttpHandler next, final Set<HttpString> allowedMethods) {
        this.allowedMethods = new HashSet<>(allowedMethods);
        this.next = next;
    }

    public AllowedMethodsHandler(final HttpHandler next, final HttpString... allowedMethods) {
        this.allowedMethods = new HashSet<>(Arrays.asList(allowedMethods));
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (allowedMethods.contains(exchange.getRequestMethod())) {
            next.handleRequest(exchange);
        } else {
            exchange.setResponseCode(StatusCodes.METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }

}
