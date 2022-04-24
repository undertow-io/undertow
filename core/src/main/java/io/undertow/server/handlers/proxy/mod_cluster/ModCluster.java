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

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.undertow.client.UndertowClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.handlers.proxy.RouteParsingStrategy;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;

/**
 * @author Emanuel Muckenhuber
 */
public class ModCluster {

    // Health check intervals
    private final long healthCheckInterval;
    private final long removeBrokenNodes;
    private final NodeHealthChecker healthChecker;

    // Proxy connection pool defaults
    private final int maxConnections;
    private final int cacheConnections;
    private final int requestQueueSize;
    private final boolean queueNewRequests;
    private final int maxRequestTime;
    private final long ttl;
    private final boolean useAlias;

    private final XnioWorker xnioWorker;
    private final ModClusterContainer container;
    private final int maxRetries;
    private final boolean deterministicFailover;
    private final RouteParsingStrategy routeParsingStrategy;
    private final String rankedAffinityDelimiter;

    private final boolean reuseXForwarded;

    private final String serverID = UUID.randomUUID().toString(); // TODO

    ModCluster(Builder builder) {
        this.xnioWorker = builder.xnioWorker;
        this.maxConnections = builder.maxConnections;
        this.cacheConnections = builder.cacheConnections;
        this.requestQueueSize = builder.requestQueueSize;
        this.queueNewRequests = builder.queueNewRequests;
        this.healthCheckInterval = builder.healthCheckInterval;
        this.removeBrokenNodes = builder.removeBrokenNodes;
        this.deterministicFailover = builder.deterministicFailover;
        this.routeParsingStrategy = builder.routeParsingStrategy;
        this.rankedAffinityDelimiter = builder.rankedAffinityDelimiter;
        this.healthChecker = builder.healthChecker;
        this.maxRequestTime = builder.maxRequestTime;
        this.ttl = builder.ttl;
        this.useAlias = builder.useAlias;
        this.maxRetries = builder.maxRetries;
        this.reuseXForwarded = builder.reuseXForwarded;
        this.container = new ModClusterContainer(this, builder.xnioSsl, builder.client, builder.clientOptions);
    }

    protected String getServerID() {
        return serverID;
    }

    protected ModClusterContainer getContainer() {
        return container;
    }

    public ModClusterController getController() {
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

    public long getTtl() {
        return ttl;
    }

    public boolean isUseAlias() {
        return useAlias;
    }

    public boolean isDeterministicFailover() {
        return deterministicFailover;
    }

    public RouteParsingStrategy routeParsingStrategy() {
        return this.routeParsingStrategy;
    }

    public String rankedAffinityDelimiter() {
        return this.rankedAffinityDelimiter;
    }

    /**
     * Get the handler proxying the requests.
     *
     * @return the proxy handler
     */
    @Deprecated
    public HttpHandler getProxyHandler() {
        return createProxyHandler();
    }
    /**
     * Get the handler proxying the requests.
     *
     * @return the proxy handler
     */
    public HttpHandler createProxyHandler() {
        return ProxyHandler.builder()
                .setProxyClient(container.getProxyClient())
                .setMaxRequestTime(maxRequestTime)
                .setMaxConnectionRetries(maxRetries)
                .setReuseXForwarded(reuseXForwarded)
                .build();
    }

    /**
     * Get the handler proxying the requests.
     *
     * @return the proxy handler
     */
    public HttpHandler createProxyHandler(HttpHandler next) {
        return ProxyHandler.builder()
                .setProxyClient(container.getProxyClient())
                .setNext(next)
                .setMaxRequestTime(maxRequestTime)
                .setMaxConnectionRetries(maxRetries)
                .setReuseXForwarded(reuseXForwarded)
                .build();
    }
    /**
     * Start
     */
    public synchronized void start() {

    }

    /**
     * Start advertising a mcmp handler.
     *
     * @param config the mcmp handler config
     * @throws IOException
     */
    public synchronized void advertise(MCMPConfig config) throws IOException {
        checkNotNullParam("config", config);
        final MCMPConfig.AdvertiseConfig advertiseConfig = checkNotNullParam("config.getAdvertiseConfig", config.getAdvertiseConfig());

        MCMPAdvertiseTask.advertise(container, advertiseConfig, xnioWorker);
    }

    /**
     * Stop
     */
    public synchronized void stop() {

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
        private int cacheConnections = 1;
        private int requestQueueSize = 0;
        private boolean queueNewRequests = false;

        private int maxRequestTime = -1;
        private long ttl = TimeUnit.SECONDS.toMillis(60);
        private boolean useAlias = false;

        private NodeHealthChecker healthChecker = NodeHealthChecker.NO_CHECK;
        private long healthCheckInterval = TimeUnit.SECONDS.toMillis(10);
        private long removeBrokenNodes = TimeUnit.MINUTES.toMillis(1);
        private OptionMap clientOptions = OptionMap.EMPTY;
        private int maxRetries;
        private boolean deterministicFailover = false;
        private RouteParsingStrategy routeParsingStrategy = RouteParsingStrategy.SINGLE;
        private String rankedAffinityDelimiter = ".";

        private boolean reuseXForwarded;

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

        public Builder setUseAlias(boolean useAlias) {
            this.useAlias = useAlias;
            return this;
        }

        public Builder setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder setDeterministicFailover(boolean deterministicFailover) {
            this.deterministicFailover = deterministicFailover;
            return this;
        }

        /**
         * Configures route parsing strategy to support none, single or ranked affinity.
         *
         * @param routeParsingStrategy strategy to use for parsing routes
         * @return this builder
         */
        public Builder setRouteParsingStrategy(RouteParsingStrategy routeParsingStrategy) {
            this.routeParsingStrategy = routeParsingStrategy;
            return this;
        }

        /**
         * Configures ranked affinity delimiter used for splitting multiple encoded routes when
         * {@link RouteParsingStrategy#RANKED} is specified. Web requests will have an affinity for the first available node in
         * the list.
         *
         * @param rankedAffinityDelimiter delimiter splitting multiple routes; typically a "."
         * @return this builder
         */
        public Builder setRankedAffinityDelimiter(String rankedAffinityDelimiter) {
            this.rankedAffinityDelimiter = rankedAffinityDelimiter;
            return this;
        }

        public Builder setTtl(long ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder setClientOptions(OptionMap clientOptions) {
            this.clientOptions = clientOptions;
            return this;
        }

        public Builder setReuseXForwarded(boolean reuseXForwarded) {
            this.reuseXForwarded = reuseXForwarded;
            return this;
        }
    }

}
