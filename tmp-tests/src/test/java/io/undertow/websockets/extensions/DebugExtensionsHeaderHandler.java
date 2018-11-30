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

package io.undertow.websockets.extensions;

import io.undertow.UndertowLogger;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;

/**
 * A {@link HttpHandler} implementation used for debugging WebSocket headers parameters.
 *
 * @author Lucas Ponce
 */
public class DebugExtensionsHeaderHandler implements HttpHandler {

    private final HttpHandler next;
    private HeaderValues requestExtensions;
    private HeaderValues responseExtensions;

    public DebugExtensionsHeaderHandler(final HttpHandler next) {
        this.next = next;
    }

    public HeaderValues getRequestExtensions() {
        return requestExtensions;
    }

    public HeaderValues getResponseExtensions() {
        return responseExtensions;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        final StringBuilder sb = new StringBuilder();
        requestExtensions = exchange.getRequestHeaders().get(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING);
        if (requestExtensions != null) {

            for (String value : requestExtensions) {
                sb.append("\n")
                        .append("--- REQUEST ---")
                        .append("\n")
                        .append(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING)
                        .append(": ")
                        .append(value)
                        .append("\n");
            }

            exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {

                @Override
                public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {

                    responseExtensions = exchange.getResponseHeaders().get(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING);
                    if (responseExtensions != null) {
                        for (String value : responseExtensions) {
                            sb.append("\n")
                                    .append("--- RESPONSE ---")
                                    .append("\n")
                                    .append(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING)
                                    .append(": ")
                                    .append(value)
                                    .append("\n");
                        }
                    }

                    nextListener.proceed();
                    UndertowLogger.REQUEST_DUMPER_LOGGER.info(sb.toString());
                }
            });

        }

        next.handleRequest(exchange);
    }
}
