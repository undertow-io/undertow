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
 * Handler that blacklists certain HTTP methods.
 *
 * @author Stuart Douglas
 */
public class DisallowedMethodsHandler implements HttpHandler {

    private final Set<HttpString> disallowedMethods;
    private final HttpHandler next;

    public DisallowedMethodsHandler(final HttpHandler next, final Set<HttpString> disallowedMethods) {
        this.disallowedMethods = new HashSet<>(disallowedMethods);
        this.next = next;
    }


    public DisallowedMethodsHandler(final HttpHandler next, final HttpString... disallowedMethods) {
        this.disallowedMethods = new HashSet<>(Arrays.asList(disallowedMethods));
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (disallowedMethods.contains(exchange.getRequestMethod())) {
            exchange.setResponseCode(StatusCodes.METHOD_NOT_ALLOWED);
            exchange.endExchange();
        } else {
            next.handleRequest(exchange);
        }
    }

}
