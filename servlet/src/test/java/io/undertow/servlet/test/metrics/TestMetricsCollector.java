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

package io.undertow.servlet.test.metrics;

import io.undertow.server.handlers.MetricsHandler;
import io.undertow.servlet.api.MetricsCollector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class TestMetricsCollector implements MetricsCollector {

    private final ConcurrentMap<String,MetricsHandler> metrics = new ConcurrentHashMap<>();

    @Override
    public void registerMetric(String name, MetricsHandler handler) {
        metrics.putIfAbsent(name,handler);
    }

    public MetricsHandler.MetricResult getMetrics(String name) {
        return metrics.get(name).getMetrics();
    }

}
