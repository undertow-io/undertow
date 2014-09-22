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

import java.net.URI;
import java.net.URISyntaxException;

/**
 * The node configuration.
 *
 * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
 * @author Emanuel Muckenhuber
 */
public class NodeConfig {

    /**
     * The JVM Route.
     */
    private final String jvmRoute;

    /**
     * The connection URI.
     */
    private final URI connectionURI;

    /**
     * The balancer configuration to use.
     */
    private final String balancer;

    /**
     * The failover domain.
     */
    private final String domain;

    /**
     * Tell how to flush the packets. On: Send immediately, Auto wait for flushwait time before sending, Off don't flush.
     * Default: "Off"
     */
    private boolean flushPackets;

    /**
     * Time to wait before flushing. Value in milliseconds. Default: 10
     */
    private final int flushwait;

    /**
     * Time to wait for a pong answer to a ping. 0 means we don't try to ping before sending. Value in seconds Default: 10
     * (10_000 in milliseconds)
     */
    private final int ping;

    /**
     * max time in seconds to life for connection above smax. Default 60 seconds (60_000 in milliseconds).
     */
    private final int ttl;

    /**
     * Max time the proxy will wait for the backend connection. Default 0 no timeout value in seconds.
     */
    private final int timeout;

    // Proxy connection pool defaults
    private final int maxConnections;
    private final int cacheConnections;
    private final int requestQueueSize;
    private final boolean queueNewRequests;

    NodeConfig(NodeBuilder b, final URI connectionURI) {
        this.connectionURI = connectionURI;
        balancer = b.balancer;
        domain = b.domain;
        jvmRoute = b.jvmRoute;
        flushPackets = b.flushPackets;
        flushwait = b.flushwait;
        ping = b.ping;
        ttl = b.ttl;
        timeout = b.timeout;
        maxConnections = b.maxConnections;
        cacheConnections = b.cacheConnections;
        requestQueueSize = b.requestQueueSize;
        queueNewRequests = b.queueNewRequests;
    }

    /**
     * Get the connection URI.
     *
     * @return the connection URI
     */
    public URI getConnectionURI() {
        return connectionURI;
    }

    /**
     * Getter for domain
     *
     * @return the domain
     */
    public String getDomain() {
        return this.domain;
    }

    /**
     * Getter for flushwait
     *
     * @return the flushwait
     */
    public int getFlushwait() {
        return this.flushwait;
    }

    /**
     * Getter for ping
     *
     * @return the ping
     */
    public int getPing() {
        return this.ping;
    }

    /**
     * Getter for smax
     *
     * @return the smax
     */
    public int getSmax() {
        return this.cacheConnections;
    }

    /**
     * Getter for ttl
     *
     * @return the ttl
     */
    public int getTtl() {
        return this.ttl;
    }

    /**
     * Getter for timeout
     *
     * @return the timeout
     */
    public int getTimeout() {
        return this.timeout;
    }

    /**
     * Getter for balancer
     *
     * @return the balancer
     */
    public String getBalancer() {
        return this.balancer;
    }

    public boolean isFlushPackets() {
        return flushPackets;
    }

    public void setFlushPackets(boolean flushPackets) {
        this.flushPackets = flushPackets;
    }

    public String getJvmRoute() {
        return jvmRoute;
    }

    /**
     * Get the maximum connection limit for a nodes thread-pool.
     *
     * @return the max connections limit
     */
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Get the amount of connections which should be kept alive in the connection pool.
     *
     * @return the number of cached connections
     */
    public int getCacheConnections() {
        return cacheConnections;
    }

    /**
     * Get the max queue size for requests.
     *
     * @return the queue size for requests
     */
    public int getRequestQueueSize() {
        return requestQueueSize;
    }

    /**
     * Flag indicating whether requests without a session can be queued.
     *
     * @return true if requests without a session id can be queued
     */
    public boolean isQueueNewRequests() {
        return queueNewRequests;
    }

    public static NodeBuilder builder(ModCluster modCluster) {
        return new NodeBuilder(modCluster);
    }

    public static class NodeBuilder {

        private String jvmRoute;
        private String balancer = "mycluster";
        private String domain = null;

        private String type = "http";
        private String hostname;
        private int port;

        private boolean flushPackets = false;
        private int flushwait = 10;
        private int ping = 10000;

        private int maxConnections;
        private int cacheConnections;
        private int requestQueueSize;
        private boolean queueNewRequests = false;

        private int ttl = 60000;
        private int timeout = 0;

        NodeBuilder(final ModCluster modCluster) {
            this.maxConnections = modCluster.getMaxConnections();
            this.cacheConnections = modCluster.getCacheConnections();
            this.requestQueueSize = modCluster.getRequestQueueSize();
            this.queueNewRequests = modCluster.isQueueNewRequests();
        }

        public NodeBuilder setHostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public NodeBuilder setPort(int port) {
            this.port = port;
            return this;
        }

        public NodeBuilder setType(String type) {
            this.type = type;
            return this;
        }

        public NodeBuilder setBalancer(String balancer) {
            this.balancer = balancer;
            return this;
        }

        public NodeBuilder setDomain(String domain) {
            this.domain = domain;
            return this;
        }

        public NodeBuilder setJvmRoute(String jvmRoute) {
            this.jvmRoute = jvmRoute;
            return this;
        }

        public NodeBuilder setFlushPackets(boolean flushPackets) {
            this.flushPackets = flushPackets;
            return this;
        }

        public NodeBuilder setFlushwait(int flushwait) {
            this.flushwait = flushwait;
            return this;
        }

        public NodeBuilder setPing(int ping) {
            this.ping = ping;
            return this;
        }

        public NodeBuilder setSmax(int smax) {
            this.cacheConnections = smax;
            return this;
        }

        public NodeBuilder setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public NodeBuilder setCacheConnections(int cacheConnections) {
            this.cacheConnections = cacheConnections;
            return this;
        }

        public NodeBuilder setRequestQueueSize(int requestQueueSize) {
            this.requestQueueSize = requestQueueSize;
            return this;
        }

        public NodeBuilder setQueueNewRequests(boolean queueNewRequests) {
            this.queueNewRequests = queueNewRequests;
            return this;
        }

        public NodeBuilder setTtl(int ttl) {
            this.ttl = ttl;
            return this;
        }

        public NodeBuilder setTimeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public NodeConfig build() throws URISyntaxException {
            final URI uri = new URI(type, null, hostname, port, "/", "", "");
            return new NodeConfig(this, uri);
        }
    }

}
