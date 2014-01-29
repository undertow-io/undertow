package io.undertow.servlet.core;

import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.MetricsHandler;
import io.undertow.servlet.api.MetricsCollector;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.handlers.ServletRequestContext;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class MetricsChainHandler implements HttpHandler {

    public static final HandlerWrapper WRAPPER = new HandlerWrapper() {
        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new MetricsChainHandler(handler);
        }
    };

    private final HttpHandler next;

    public MetricsChainHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        ServletRequestContext context = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        ServletInfo servletInfo = context.getCurrentServlet().getManagedServlet().getServletInfo();
        MetricsCollector collector = context.getDeployment().getDeploymentInfo().getMetricsCollector();
        MetricsHandler metricsHandler = collector.getHandlerForMetric(servletInfo.getName());
        if (metricsHandler==null){
            metricsHandler = new MetricsHandler(next);
            collector.registerMetric(servletInfo.getName(), metricsHandler);
        }
        metricsHandler.handleRequest(exchange);
    }
}
