/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.undertow.UndertowMessages;
import io.undertow.predicate.Predicate;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.RequestStatistics;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.util.FastConcurrentDirectDeque;

/**
 * This handler will track all active requests. If the predicate is either null or true, a RequestStatistics object
 * is created and stored in the handler. Once the request is finished, the RequestStatistics object is removed from
 * the local store, and flow continues to the next listener.
 *
 * @author <a href="mailto:jasondlee@redhat.com">Jason Lee</a>
 */
public final class ActiveRequestTrackerHandler implements HttpHandler {
    private final FastConcurrentDirectDeque<RequestStatistics> trackedRequests = new FastConcurrentDirectDeque<>();
    private final HttpHandler next;
    private final Predicate predicate;

    public ActiveRequestTrackerHandler(HttpHandler next, Predicate predicate) {
        if (next == null) {
            throw UndertowMessages.MESSAGES.argumentCannotBeNull("next");
        }

        this.predicate = predicate;
        this.next = next;
    }

    public List<RequestStatistics> getTrackedRequests() {
        return new ArrayList<>(trackedRequests);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (predicate == null || predicate.resolve(exchange)) {
            final RequestStatistics stats = new RequestStatistics(exchange);

            trackedRequests.add(stats);
            exchange.addExchangeCompleteListener((exchange1, nextListener) -> {
                trackedRequests.remove(stats);
                nextListener.proceed();
            });
        }
        next.handleRequest(exchange);
    }

    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "track-request";
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
            return new ActiveRequestTrackerHandler.Wrapper();
        }

    }

    private static class Wrapper implements HandlerWrapper {
        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new ActiveRequestTrackerHandler(handler, null);
        }
    }
}
