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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Handler for requests that require 100-continue responses. If an attempt is made to read from the source
 * channel then a 100 continue response is sent.
 *
 * @author Stuart Douglas
 */
public class HttpContinueReadHandler implements HttpHandler {

    private final HttpHandler handler;

    public HttpContinueReadHandler(final HttpHandler handler) {
        this.handler = handler;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
//        if (HttpContinue.requiresContinueResponse(exchange)) {
//            exchange.addRequestWrapper(WRAPPER);
//        }
        handler.handleRequest(exchange);
    }
}
