package io.undertow.servlet.test.metrics;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.undertow.server.handlers.MetricsHandler;
import io.undertow.servlet.api.MetricsCollector;

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

    @Override
    public MetricsHandler getHandlerForMetric(String name) {
        return metrics.get(name);
    }

    @Override
    public List<String> getRegisteredMetricNames() {
        return new LinkedList<String>(metrics.keySet());
    }
}
