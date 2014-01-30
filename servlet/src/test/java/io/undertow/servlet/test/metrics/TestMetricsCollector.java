package io.undertow.servlet.test.metrics;

import io.undertow.server.handlers.MetricsHandler;
import io.undertow.servlet.api.MetricsCollector;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class TestMetricsCollector implements MetricsCollector {

    private final ConcurrentHashMap<String,MetricsHandler> metrics = new ConcurrentHashMap<String, MetricsHandler>();

    @Override
    public void registerMetric(String name, MetricsHandler handler) {
        metrics.putIfAbsent(name,handler);
    }

    public MetricsHandler.MetricResult getMetrics(String name) {
        return metrics.get(name).getMetrics();
    }

}
