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

package io.undertow.servlet.core;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.MetricsHandler;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.MetricsCollector;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.handlers.ServletHandler;
import io.undertow.servlet.handlers.ServletRequestContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
class MetricsChainHandler implements HttpHandler {


    private final HttpHandler next;
    private final Map<String, MetricsHandler> servletHandlers;

    MetricsChainHandler(HttpHandler next, MetricsCollector collector, Deployment deployment) {
        this.next = next;
        final Map<String, MetricsHandler> servletHandlers = new HashMap<>();
        for(Map.Entry<String, ServletHandler> entry : deployment.getServlets().getServletHandlers().entrySet()) {
            MetricsHandler handler = new MetricsHandler(next);
            servletHandlers.put(entry.getKey(), handler);
            collector.registerMetric(entry.getKey(), handler);
        }
        this.servletHandlers = Collections.unmodifiableMap(servletHandlers);
    }
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        ServletRequestContext context = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        ServletInfo servletInfo = context.getCurrentServlet().getManagedServlet().getServletInfo();
        MetricsHandler handler = servletHandlers.get(servletInfo.getName());
        if(handler != null) {
            handler.handleRequest(exchange);
        } else {
            next.handleRequest(exchange);
        }
    }
}
