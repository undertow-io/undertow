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
package io.undertow.websockets.jsr.handshake;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.websocket.HandshakeResponse;

import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * {@link HandshakeResponse} which wraps a {@link io.undertow.websockets.spi.WebSocketHttpExchange} to act on it.
 * Once the processing of it is done {@link #update()} must be called to persist any changes
 * made.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class ExchangeHandshakeResponse implements HandshakeResponse {
    private final WebSocketHttpExchange exchange;
    private Map<String, List<String>> headers;

    public ExchangeHandshakeResponse(final WebSocketHttpExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        if (headers == null) {
            headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            headers.putAll(exchange.getResponseHeaders());
        }
        return headers;
    }

    /**
     * Persist all changes and update the wrapped {@link io.undertow.websockets.spi.WebSocketHttpExchange}.
     */
    void update() {
        if (headers != null) {
            exchange.setResponseHeaders(headers);
        }
    }
}
