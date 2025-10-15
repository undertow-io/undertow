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

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.anyAreSet;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.ConnectionPoolManager;
import io.undertow.server.handlers.proxy.ProxyConnectionPool;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import org.xnio.XnioIoThread;

/**
 * @author Stuart Douglas
 * @author Emanuel Muckenhuber
 */
class Node {

    private final int id;
    private final String jvmRoute;
    private final ConnectionPoolManager connectionPoolManager;
    private final NodeConfig nodeConfig;
    private final Balancer balancerConfig;
    private final ProxyConnectionPool connectionPool;
    private final NodeLbStatus lbStatus = new NodeLbStatus();
    private final ModClusterContainer container;
    private final List<VHostMapping> vHosts = new CopyOnWriteArrayList<>();
    private final List<Context> contexts = new CopyOnWriteArrayList<>();

    private final XnioIoThread ioThread;
    private final ByteBufferPool bufferPool;

    private volatile int state = ERROR; // This gets cleared with the first status report

    private static final int ERROR = 1 << 31;
    private static final int REMOVED = 1 << 30;
    private static final int HOT_STANDBY = 1 << 29;
    private static final int ACTIVE_PING = 1 << 28;
    private static final int ERROR_MASK = (1 << 10) - 1;

    private static final AtomicInteger idGen = new AtomicInteger();
    private static final AtomicIntegerFieldUpdater<Node> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(Node.class, "state");

    protected Node(NodeConfig nodeConfig, Balancer balancerConfig, XnioIoThread ioThread, ByteBufferPool bufferPool, ModClusterContainer container) {
        this.id = idGen.incrementAndGet();
        this.jvmRoute = nodeConfig.getJvmRoute();
        this.nodeConfig = nodeConfig;
        this.ioThread = ioThread;
        this.bufferPool = bufferPool;
        this.balancerConfig = balancerConfig;
        this.container = container;
        this.connectionPoolManager = new NodeConnectionPoolManager();
        this.connectionPool = new ProxyConnectionPool(connectionPoolManager, nodeConfig.getConnectionURI(), container.getXnioSsl(), container.getClient(), container.getClientOptions());
    }

    public int getId() {
        return id;
    }

    /**
     * Get the JVM route.
     *
     * @return the jvmRoute
     */
    public String getJvmRoute() {
        return jvmRoute;
    }

    public Balancer getBalancer() {
        return balancerConfig;
    }

    public NodeConfig getNodeConfig() {
        return nodeConfig;
    }

    /**
     * Get or create the connection pool for this node.
     *
     * @return the connection pool
     */
    public ProxyConnectionPool getConnectionPool() {
        return connectionPool;
    }

    XnioIoThread getIoThread() {
        return ioThread;
    }

    public NodeStatus getStatus() {
        final int status = this.state;
        if (anyAreSet(status, ERROR)) {
            return NodeStatus.NODE_DOWN;
        } else if (anyAreSet(status, HOT_STANDBY)) {
            return NodeStatus.NODE_HOT_STANDBY;
        } else {
            return NodeStatus.NODE_UP;
        }
    }

    public int getElected() {
        return lbStatus.getElected();
    }

    int getElectedDiff() {
        return lbStatus.getElectedDiff();
    }

    /**
     * Get the load information. Add the error information for clients.
     *
     * @return the node load
     */
    public int getLoad() {
        final int status = this.state;
        if (anyAreSet(status, ERROR)) {
            return -1;
        } else if (anyAreSet(status, HOT_STANDBY)) {
            return 0;
        } else {
            return lbStatus.getLbFactor();
        }
    }

    /**
     * Get the current load status, based on the number of elections and the current load;
     *
     * @return the load status
     */
    public int getLoadStatus() {
        return lbStatus.getLbStatus();
    }

    /**
     * This node got elected to serve a request!
     */
    void elected() {
        lbStatus.elected();
    }

    List<VHostMapping> getVHosts() {
        return Collections.unmodifiableList(vHosts);
    }

    Collection<Context> getContexts() {
        return Collections.unmodifiableCollection(contexts);
    }

    void resetLbStatus() {
        if (allAreClear(state, ERROR)) {
            if (lbStatus.update()) {
                return;
            }
        }
    }

    /**
     * Check the health of the node and try to ping it if necessary.
     *
     * @param threshold     the threshold after which the node should be removed
     * @param healthChecker the node health checker
     */
    protected void checkHealth(long threshold, NodeHealthChecker healthChecker) {
        final int state = this.state;
        if (anyAreSet(state, REMOVED | ACTIVE_PING)) {
            return;
        }
        healthCheckPing(threshold, healthChecker);
    }

    void healthCheckPing(final long threshold, NodeHealthChecker healthChecker) {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            if ((oldState & ACTIVE_PING) != 0) {
                // There is already a ping active
                return;
            }
            newState = oldState | ACTIVE_PING;
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                break;
            }
        }

        NodePingUtil.internalPingNode(this, new NodePingUtil.PingCallback() {
            @Override
            public void completed() {
                clearActivePing();
            }

            @Override
            public void failed() {
                if (healthCheckFailed() == threshold) {
                    // Remove using the executor task pool
                    ioThread.getWorker().execute(new Runnable() {
                        @Override
                        public void run() {
                            container.removeNode(Node.this, true);
                            clearActivePing();
                        }
                    });
                } else {
                    clearActivePing();
                }
            }
        }, healthChecker, ioThread, bufferPool, container.getClient(), container.getXnioSsl(), OptionMap.EMPTY);
    }

    /**
     * Async ping from the user
     *
     * @param exchange    the http server exchange
     * @param callback    the ping callback
     */
    void ping(final HttpServerExchange exchange, final NodePingUtil.PingCallback callback) {
        NodePingUtil.pingNode(this, exchange, callback);
    }

    /**
     * Register a context.
     *
     * @param path the context path
     * @return the created context
     */
    Context registerContext(final String path, final List<String> virtualHosts) {
        VHostMapping host = null;
        for (final VHostMapping vhost : vHosts) {
            if (virtualHosts.equals(vhost.getAliases())) {
                host = vhost;
                break;
            }
        }
        if (host == null) {
            host = new VHostMapping(this, virtualHosts);
            vHosts.add(host);
        }
        final Context context = new Context(path, host, this);
        contexts.add(context);
        return context;
    }

    /**
     * Get a context.
     *
     * @param path       the context path
     * @param aliases    the aliases
     * @return the context, {@code null} if there is no matching context
     */
    Context getContext(final String path, List<String> aliases) {
        VHostMapping host = null;
        for (final VHostMapping vhost : vHosts) {
            if (aliases.equals(vhost.getAliases())) {
                host = vhost;
                break;
            }
        }
        if (host == null) {
            return null;
        }
        for (final Context context : contexts) {
            if (context.getPath().equals(path) && context.getVhost() == host) {
                return context;
            }
        }
        return null;
    }

    Context removeContext(final String path, final List<String> aliases) {
        final Context context = getContext(path, aliases);
        if (context != null) {
            context.stop();
            contexts.remove(context);
            return context;
        }
        return null;
    }

    protected void updateLoad(final int i) {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            newState = oldState & ~(ERROR | HOT_STANDBY | ERROR_MASK);
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                lbStatus.updateLoad(i);
                return;
            }
        }
    }

    protected void hotStandby() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            newState = oldState & ~(ERROR | ERROR_MASK) | HOT_STANDBY;
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                lbStatus.updateLoad(0);
                return;
            }
        }
    }

    protected void markRemoved() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            newState = oldState | REMOVED;
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                connectionPool.close();
                return;
            }
        }
    }

    protected void markInError() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            newState = oldState | ERROR;
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                UndertowLogger.ROOT_LOGGER.nodeIsInError(jvmRoute);
                return;
            }
        }
    }

    private void clearActivePing() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            newState = oldState & ~ACTIVE_PING;
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                return;
            }
        }
    }

    /**
     * Mark a node in error. Mod_cluster has a threshold after which broken nodes get removed.
     *
     * @return
     */
    private int healthCheckFailed() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            if ((oldState & ERROR) != ERROR) {
                newState = oldState | ERROR;
                UndertowLogger.ROOT_LOGGER.nodeIsInError(jvmRoute);
            } else if ((oldState & ERROR_MASK) == ERROR_MASK) {
                return ERROR_MASK;
            } else {
                newState = oldState +1;
            }
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                return newState & ERROR_MASK;
            }
        }
    }

    protected void resetState() {
        state = ERROR;
        lbStatus.updateLoad(0);
    }

    protected boolean isInErrorState() {
        return (state & ERROR) == ERROR;
    }

    boolean isHotStandby() {
        return anyAreSet(state, HOT_STANDBY);
    }

    protected boolean checkAvailable(final boolean existingSession) {
        if (allAreClear(state, ERROR | REMOVED)) {
            // Check the state of the queue on the connection pool
            final ProxyConnectionPool.AvailabilityType availability = connectionPool.available();
            if (availability == ProxyConnectionPool.AvailabilityType.AVAILABLE) {
                return true;
            } else if (availability == ProxyConnectionPool.AvailabilityType.FULL) {
                if (existingSession) {
                    return true;
                } else if (nodeConfig.isQueueNewRequests()) {
                    return true;
                }
            }
        }
        return false;
    }

    private class NodeConnectionPoolManager implements ConnectionPoolManager {

        @Override
        public boolean isAvailable() {
            return allAreClear(state, ERROR | REMOVED);
        }

        @Override
        public boolean handleError() {
            markInError();
            return false;
        }

        @Override
        public boolean clearError() {
            // This needs to be cleared through the status update
            return isAvailable();
        }

        @Override
        public int getMaxConnections() {
            return nodeConfig.getMaxConnections();
        }

        @Override
        public int getMaxCachedConnections() {
            return nodeConfig.getMaxConnections();
        }

        @Override
        public int getSMaxConnections() {
            return nodeConfig.getSmax();
        }

        @Override
        public long getTtl() {
            return nodeConfig.getTtl();
        }

        @Override
        public int getMaxQueueSize() {
            return nodeConfig.getRequestQueueSize();
        }

        @Override
        public int getProblemServerRetry() {
            return -1; // Disable ping from the pool, this is handled through the health-check
        }
    }

    // Simple host mapping for the mod cluster management protocol
    static final AtomicInteger vHostIdGen = new AtomicInteger();
    static class VHostMapping {

        private final int id;
        private final List<String> aliases;
        private final Node node;

        VHostMapping(Node node, List<String> aliases) {
            this.id = vHostIdGen.incrementAndGet();
            this.aliases = aliases;
            this.node = node;
        }

        public int getId() {
            return id;
        }

        public List<String> getAliases() {
            return aliases;
        }

        Node getNode() {
            return node;
        }
    }

    @Override
    public String toString() {
        return "Node{" +
                "jvmRoute='" + jvmRoute + '\'' +
                ", contexts=" + contexts +
                '}';
    }
}
