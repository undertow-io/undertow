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

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.util.TreeMap;

/**
 * Handler that sets up the predicate context
 *
 * @author Stuart Douglas
 */
public class PredicateContextHandler implements HttpHandler {

    private final HttpHandler next;

    public PredicateContextHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.putAttachment(Predicate.PREDICATE_CONTEXT, new TreeMap<String, Object>());
        next.handleRequest(exchange);
    }
}
