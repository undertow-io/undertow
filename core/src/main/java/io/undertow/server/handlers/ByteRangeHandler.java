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

import io.undertow.conduits.HeadStreamSinkConduit;
import io.undertow.conduits.RangeStreamSinkConduit;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ResponseCommitListener;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.ByteRange;
import io.undertow.util.ConduitFactory;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import org.xnio.conduits.StreamSinkConduit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler for Range requests. This is a generic handler that can handle range requests to any resource
 * of a fixed content length i.e. any resource where the content-length header has been set.
 *
 * Note that this is not necessarily the most efficient way to handle range requests, as the full content
 * will be generated and then discarded.
 *
 * At present this handler can only handle simple (i.e. single range) requests. If multiple ranges are requested the
 * Range header will be ignored.
 *
 * @author Stuart Douglas
 */
public class ByteRangeHandler implements HttpHandler {

    private final HttpHandler next;
    private final boolean sendAcceptRanges;

    private static final ResponseCommitListener ACCEPT_RANGE_LISTENER = new ResponseCommitListener() {
        @Override
        public void beforeCommit(HttpServerExchange exchange) {
            if(!exchange.getResponseHeaders().contains(Headers.ACCEPT_RANGES)) {
                if (exchange.getResponseHeaders().contains(Headers.CONTENT_LENGTH)) {
                    exchange.getResponseHeaders().put(Headers.ACCEPT_RANGES, "bytes");
                } else {
                    exchange.getResponseHeaders().put(Headers.ACCEPT_RANGES, "none");
                }
            }
        }

    };

    public ByteRangeHandler(HttpHandler next, boolean sendAcceptRanges) {
        this.next = next;
        this.sendAcceptRanges = sendAcceptRanges;
    }


    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        //range requests are only support for GET requests as per the RFC
        if(!Methods.GET.equals(exchange.getRequestMethod()) && !Methods.HEAD.equals(exchange.getRequestMethod())) {
            next.handleRequest(exchange);
            return;
        }
        if (sendAcceptRanges) {
            exchange.addResponseCommitListener(ACCEPT_RANGE_LISTENER);
        }
        final ByteRange range = ByteRange.parse(exchange.getRequestHeaders().getFirst(Headers.RANGE));
        if (range != null && range.getRanges() == 1) {
            exchange.addResponseWrapper(new ConduitWrapper<StreamSinkConduit>() {
                @Override
                public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
                    if(exchange.getStatusCode() != StatusCodes.OK ) {
                        return factory.create();
                    }
                    String length = exchange.getResponseHeaders().getFirst(Headers.CONTENT_LENGTH);
                    if (length == null) {
                        return factory.create();
                    }
                    long responseLength = Long.parseLong(length);
                    ByteRange.RangeResponseResult rangeResponse = range.getResponseResult(responseLength, exchange.getRequestHeaders().getFirst(Headers.IF_RANGE), DateUtils.parseDate(exchange.getResponseHeaders().getFirst(Headers.LAST_MODIFIED)), exchange.getResponseHeaders().getFirst(Headers.ETAG));
                    if(rangeResponse != null){
                        long start = rangeResponse.getStart();
                        long end = rangeResponse.getEnd();
                        exchange.setStatusCode(rangeResponse.getStatusCode());
                        exchange.getResponseHeaders().put(Headers.CONTENT_RANGE, rangeResponse.getContentRange());
                        exchange.setResponseContentLength(rangeResponse.getContentLength());
                        if(rangeResponse.getStatusCode() == StatusCodes.REQUEST_RANGE_NOT_SATISFIABLE) {
                            return new HeadStreamSinkConduit(factory.create(), null, true);
                        }
                        return new RangeStreamSinkConduit(factory.create(), start, end, responseLength);
                    } else {
                        return factory.create();
                    }
                }
            });
        }
        next.handleRequest(exchange);

    }

    public static class Wrapper implements HandlerWrapper {

        private final boolean sendAcceptRanges;

        public Wrapper(boolean sendAcceptRanges) {
            this.sendAcceptRanges = sendAcceptRanges;
        }

        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new ByteRangeHandler(handler, sendAcceptRanges);
        }
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "byte-range";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            Map<String, Class<?>> params = new HashMap<>();
            params.put("send-accept-ranges", boolean.class);
            return params;
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return "send-accept-ranges";
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {
            Boolean send = (Boolean) config.get("send-accept-ranges");
            return new Wrapper(send != null && send);
        }
    }


}
