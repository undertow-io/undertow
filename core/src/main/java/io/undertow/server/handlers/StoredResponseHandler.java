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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.xnio.conduits.StreamSinkConduit;
import io.undertow.conduits.StoredResponseStreamSinkConduit;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.ConduitFactory;

/**
 * A handler that buffers the full response and attaches it to the exchange. This makes use of
 * {@link StoredResponseStreamSinkConduit}
 * <p>
 * This will be made available once the response is fully complete, so should generally
 * be read in an {@link io.undertow.server.ExchangeCompletionListener}
 *
 * @author Stuart Douglas
 */
public class StoredResponseHandler implements HttpHandler {

    private final HttpHandler next;

    public StoredResponseHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addResponseWrapper(new ConduitWrapper<StreamSinkConduit>() {
            @Override
            public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
                return new StoredResponseStreamSinkConduit(factory.create(), exchange);
            }
        });
        next.handleRequest(exchange);
    }

    @Override
    public String toString() {
        return "store-response()";
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "store-response";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new HandlerWrapper() {
                @Override
                public HttpHandler wrap(HttpHandler handler) {
                    return new StoredResponseHandler(handler);
                }
            };
        }
    }

}
