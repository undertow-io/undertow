package io.undertow.servlet.api;

import io.undertow.server.handlers.MetricsHandler;

/**
 * An interface that can be used to collect Servlet metrics
 *
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public interface MetricsCollector {

    void registerMetric(String servletName, MetricsHandler handler);
}
