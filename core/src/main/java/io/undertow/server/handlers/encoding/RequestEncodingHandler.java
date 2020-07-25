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

package io.undertow.server.handlers.encoding;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.xnio.conduits.StreamSourceConduit;
import io.undertow.conduits.GzipStreamSourceConduit;
import io.undertow.conduits.InflatingStreamSourceConduit;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Headers;

/**
 * Handler that serves as the basis for request content encoding.
 * <p>
 * This is not part of the HTTP spec, however there are some applications where it is useful.
 * <p>
 * It behaves in a similar manner to {@link EncodingHandler}, however it deals with the requests
 * content encoding.
 *
 * @author Stuart Douglas
 */
public class RequestEncodingHandler implements HttpHandler {

    private final HttpHandler next;

    private final Map<String, ConduitWrapper<StreamSourceConduit>> requestEncodings = new CopyOnWriteMap<>();

    public RequestEncodingHandler(final HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        ConduitWrapper<StreamSourceConduit> encodings = requestEncodings.get(exchange.getRequestHeaders().getFirst(Headers.CONTENT_ENCODING));
        if (encodings != null && exchange.isRequestChannelAvailable()) {
            exchange.addRequestWrapper(encodings);
            // Nested handlers or even servlet filters may implement logic to decode encoded request data.
            // Since the data is no longer encoded, we remove the encoding header.
            exchange.getRequestHeaders().remove(Headers.CONTENT_ENCODING);
        }
        next.handleRequest(exchange);
    }

    public RequestEncodingHandler addEncoding(String name, ConduitWrapper<StreamSourceConduit> wrapper) {
        this.requestEncodings.put(name, wrapper);
        return this;
    }

    public RequestEncodingHandler removeEncoding(String encoding) {
        this.requestEncodings.remove(encoding);
        return this;
    }


    public HttpHandler getNext() {
        return next;
    }

    @Override
    public String toString() {
        return "uncompress()";
    }


    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "uncompress";
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
                    return new RequestEncodingHandler(handler)
                            .addEncoding("gzip", GzipStreamSourceConduit.WRAPPER)
                            .addEncoding("deflate", InflatingStreamSourceConduit.WRAPPER);
                }
            };
        }
    }

}
