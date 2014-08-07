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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers.proxy.mod_cluster;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.undertow.client.UndertowClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyHandler;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;

/**
 * @author Emanuel Muckenhuber
 */
public class ModCluster {

    private static final HttpHandler NEXT_HANDLER = ResponseCodeHandler.HANDLE_404;

    // Health check intervals
    private final long healthCheckInterval;
    private final long removeBrokenNodes;
    private final NodeHealthChecker healthChecker;

    // Proxy connection pool defaults
    private final int maxConnections;
    private final int cacheConnections;
    private final int requestQueueSize;
    private final boolean queueNewRequests;

    private final XnioWorker xnioWorker;
    private final ModClusterContainer container;
    private final HttpHandler proxyHandler;
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    private final String serverID = UUID.randomUUID().toString(); // TODO

    ModCluster(Builder builder) {
        this.xnioWorker = builder.xnioWorker;
        this.maxConnections = builder.maxConnections;
        this.cacheConnections = builder.cacheConnections;
        this.requestQueueSize = builder.requestQueueSize;
        this.queueNewRequests = builder.queueNewRequests;
        this.healthCheckInterval = builder.healthCheckInterval;
        this.removeBrokenNodes = builder.removeBrokenNodes;
        this.healthChecker = builder.healthChecker;
        this.container = new ModClusterContainer(this, builder.xnioSsl, builder.client);
        this.proxyHandler = new ProxyHandler(container.getProxyClient(), builder.maxRequestTime, NEXT_HANDLER);
    }

    protected String getServerID() {
        return serverID;
    }

    protected ModClusterContainer getContainer() {
        return container;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public int getCacheConnections() {
        return cacheConnections;
    }

    public int getRequestQueueSize() {
        return requestQueueSize;
    }

    public boolean isQueueNewRequests() {
        return queueNewRequests;
    }

    public long getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public long getRemoveBrokenNodes() {
        return removeBrokenNodes;
    }

    public NodeHealthChecker getHealthChecker() {
        return healthChecker;
    }

    /**
     * Get the handler proxying the requests.
     *
     * @return the proxy handler
     */
    public HttpHandler getProxyHandler() {
        return proxyHandler;
    }

    /**
     * Start
     */
    public synchronized void start() {
        if (healthCheckInterval > 0) {
            executorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    container.checkHealth();
                }
            }, healthCheckInterval, healthCheckInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Start advertising a mcmp handler.
     *
     * @param config the mcmp handler config
     * @throws IOException
     */
    public synchronized void advertise(MCMPConfig config) throws IOException {
        final MCMPConfig.AdvertiseConfig advertiseConfig = config.getAdvertiseConfig();
        if (advertiseConfig == null) {
            throw new IllegalArgumentException("advertise not enabled");
        }
        MCMPAdvertiseTask.advertise(container, advertiseConfig, xnioWorker);
    }

    /**
     * Stop
     */
    public synchronized void stop() {
        executorService.shutdownNow();
    }

    public static Builder builder(final XnioWorker worker) {
        return builder(worker, UndertowClient.getInstance(), null);
    }

    public static Builder builder(final XnioWorker worker, final UndertowClient client) {
        return builder(worker, client, null);
    }

    public static Builder builder(final XnioWorker worker, final UndertowClient client, final XnioSsl xnioSsl) {
        return new Builder(worker, client, xnioSsl);
    }

    public static class Builder {

        private final XnioSsl xnioSsl;
        private final UndertowClient client;
        private final XnioWorker xnioWorker;

        // Fairly restrictive connection pool defaults
        private int maxConnections = 16;
        private int cacheConnections = 8;
        private int requestQueueSize = 0;
        private boolean queueNewRequests = false;

        private int maxRequestTime = -1;

        private NodeHealthChecker healthChecker = NodeHealthChecker.OK;
        private long healthCheckInterval = TimeUnit.SECONDS.toMillis(10);
        private long removeBrokenNodes = TimeUnit.MINUTES.toMillis(1);

        private Builder(XnioWorker xnioWorker, UndertowClient client, XnioSsl xnioSsl) {
            this.xnioSsl = xnioSsl;
            this.client = client;
            this.xnioWorker = xnioWorker;
        }

        public ModCluster build() {
            return new ModCluster(this);
        }

        public Builder setMaxRequestTime(int maxRequestTime) {
            this.maxRequestTime = maxRequestTime;
            return this;
        }

        public Builder setHealthCheckInterval(long healthCheckInterval) {
            this.healthCheckInterval = healthCheckInterval;
            return this;
        }

        public Builder setRemoveBrokenNodes(long removeBrokenNodes) {
            this.removeBrokenNodes = removeBrokenNodes;
            return this;
        }

        public Builder setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Builder setCacheConnections(int cacheConnections) {
            this.cacheConnections = cacheConnections;
            return this;
        }

        public Builder setRequestQueueSize(int requestQueueSize) {
            this.requestQueueSize = requestQueueSize;
            return this;
        }

        public Builder setQueueNewRequests(boolean queueNewRequests) {
            this.queueNewRequests = queueNewRequests;
            return this;
        }

        public Builder setHealthChecker(NodeHealthChecker healthChecker) {
            this.healthChecker = healthChecker;
            return this;
        }
    }

}
