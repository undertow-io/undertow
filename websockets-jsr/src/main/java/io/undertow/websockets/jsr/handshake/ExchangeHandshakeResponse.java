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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletResponse;
import javax.websocket.HandshakeResponse;

/**
 * {@link HandshakeResponse} which wraps a {@link io.undertow.websockets.spi.WebSocketHttpExchange} to act on it.
 * Once the processing of it is done {@link #update()} must be called to persist any changes
 * made.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class ExchangeHandshakeResponse implements HandshakeResponse {
    private final HttpServletResponse response;
    private Map<String, List<String>> headers;

    public ExchangeHandshakeResponse(HttpServletResponse response) {
        this.response = response;
    }


    @Override
    public Map<String, List<String>> getHeaders() {
        if (headers == null) {
            headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            final Collection<String> headerNames = response.getHeaderNames();
            for (String header : headerNames) {
                headers.put(header, new ArrayList<>(response.getHeaders(header)));
            }
        }

        return headers;
    }

    /**
     * Persist all changes and update the wrapped {@link io.undertow.websockets.spi.WebSocketHttpExchange}.
     */
    void update() {
        if (headers != null) {
            for (String header : response.getHeaderNames()) {
                response.setHeader(header, null);
            }

            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                for (String val : entry.getValue()) {
                    response.addHeader(entry.getKey(), val);
                }
            }
        }
    }
}
