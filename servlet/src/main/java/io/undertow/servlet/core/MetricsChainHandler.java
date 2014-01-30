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

    public MetricsChainHandler(HttpHandler next, MetricsCollector collector, Deployment deployment) {
        this.next = next;
        final Map<String, MetricsHandler> servletHandlers = new HashMap<String, MetricsHandler>();
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
