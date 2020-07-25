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

import io.undertow.conduits.RateLimitingStreamSinkConduit;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.ConduitFactory;
import org.xnio.conduits.StreamSinkConduit;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Handler that limits the download rate
 *
 * @author Stuart Douglas
 */
public class ResponseRateLimitingHandler implements HttpHandler {

    private final long time;
    private final int bytes;
    private final HttpHandler next;

    private final ConduitWrapper<StreamSinkConduit> WRAPPER = new ConduitWrapper<StreamSinkConduit>() {
        @Override
        public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
            return new RateLimitingStreamSinkConduit(factory.create(), bytes, time, TimeUnit.MILLISECONDS);
        }
    };

    /**
     *
     * A handler that limits the download speed to a set number of bytes/period
     *
     * @param next The next handler
     * @param bytes The number of bytes per time period
     * @param time The time period
     * @param timeUnit The units of the time period
     */
    public ResponseRateLimitingHandler(HttpHandler next, int bytes,long time, TimeUnit timeUnit) {
        this.time = timeUnit.toMillis(time);
        this.bytes = bytes;
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addResponseWrapper(WRAPPER);
        next.handleRequest(exchange);
    }

    @Override
    public String toString() {
        return "response-rate-limit( bytes=" + bytes + ", time=" + time + " )";
    }


    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "response-rate-limit";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> ret = new HashMap<>();
            ret.put("bytes", Integer.class);
            ret.put("time", Long.class);
            return ret;
        }

        @Override
        public Set<String> requiredParameters() {
            return new HashSet<>(Arrays.asList("bytes", "time"));
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            return new Wrapper((Integer)config.get("bytes"), (Long)config.get("time"), TimeUnit.MILLISECONDS);
        }

    }

    private static class Wrapper implements HandlerWrapper {

        private final long time;
        private final int bytes;

        private Wrapper(int bytes, long time, TimeUnit timeUnit) {
            this.time = timeUnit.toMillis(time);
            this.bytes = bytes;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new ResponseRateLimitingHandler(handler, bytes, time, TimeUnit.MILLISECONDS);
        }
    }
}
