package io.undertow.servlet.api;

import java.util.List;

import io.undertow.server.handlers.MetricsHandler;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public interface MetricsCollector {

    void registerMetric(String name, MetricsHandler handler);

    MetricsHandler getHandlerForMetric(String name);

    List<String> getRegisteredMetricNames();
}
