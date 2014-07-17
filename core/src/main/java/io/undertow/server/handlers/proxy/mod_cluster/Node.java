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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.undertow.UndertowLogger;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.ConnectionPoolManager;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.server.handlers.proxy.ProxyConnectionPool;
import org.xnio.OptionMap;
import org.xnio.XnioIoThread;
import org.xnio.ssl.XnioSsl;

/**
 * @author Stuart Douglas
 * @author Emanuel Muckenhuber
 */
class Node {

    enum Status {
        /**
         * The node is up
         */
        NODE_UP,
        /**
         * The node is down
         */
        NODE_DOWN,
        /**
         * The node is paused
         */
        NODE_HOT_STANDBY;
    }

    private final int id;
    private final String jvmRoute;
    private final ConnectionPoolManager connectionPoolManager;
    private final NodeConfig nodeConfig;
    private final Balancer balancerConfig;
    private final ProxyConnectionPool connectionPool;
    private final NodeStats stats = new NodeStats();
    private final NodeLbStatus lbStatus = new NodeLbStatus();
    private final List<VHostMapping> vHosts = new CopyOnWriteArrayList<>();
    private final List<Context> contexts = new CopyOnWriteArrayList<>();
    private final XnioIoThread ioThread;

    private volatile int state = ERROR; // This gets cleared with the first status report

    private static final int ERROR = 1 << 31;
    private static final int REMOVED = 1 << 30;
    private static final int HOT_STANDBY = 1 << 29;
    private static final int ERROR_MASK = (1 << 10) - 1;

    private static final AtomicInteger idGen = new AtomicInteger();
    private static final AtomicIntegerFieldUpdater<Node> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(Node.class, "state");

    protected Node(NodeConfig nodeConfig, Balancer balancerConfig, XnioIoThread ioThread, XnioSsl xnioSsl, UndertowClient client) {
        this.id = idGen.incrementAndGet();
        this.jvmRoute = nodeConfig.getJvmRoute();
        this.nodeConfig = nodeConfig;
        this.ioThread = ioThread;
        this.balancerConfig = balancerConfig;
        this.connectionPoolManager = new NodeConnectionPoolManager();
        this.connectionPool = new ProxyConnectionPool(connectionPoolManager, nodeConfig.getConnectionURI(), xnioSsl, client, OptionMap.EMPTY);
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

    public NodeStats getStats() {
        return stats;
    }

    /**
     * Get or create the connection pool for this node.
     *
     * @return the connection pool
     */
    public ProxyConnectionPool getConnectionPool() {
        return connectionPool;
    }

    public Status getStatus() {
        final int status = this.state;
        if (anyAreSet(status, ERROR)) {
            return Status.NODE_DOWN;
        } else if (anyAreSet(status, HOT_STANDBY)) {
            return Status.NODE_HOT_STANDBY;
        } else {
            return Status.NODE_UP;
        }
    }

    public int getElected() {
        return lbStatus.getElected();
    }

    /**
     * Get the load information. Add the error information for clients.
     *
     * @return the node load
     */
    public int getLoad() {
        switch (getStatus()) {
            case NODE_DOWN:
                return -1;
            case NODE_HOT_STANDBY:
                return 0;
            default:
                return lbStatus.getLbfactor();
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

    protected boolean checkHealth() {
        // Check the health if the node wasn't elected or is in error state
        return !lbStatus.update() || anyAreSet(state, ERROR);
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

    /**
     * Async ping.
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

    boolean disableContext(final String path, final List<String> aliases) {
        final Context context = getContext(path, aliases);
        if (context != null) {
            context.disable();
            return true;
        }
        return false;
    }

    int stopContext(final String path, final List<String> aliases) {
        final Context context = getContext(path, aliases);
        if (context != null) {
            context.stop();
            return context.getActiveRequests();
        }
        return -1;
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

    protected void clearError() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            newState = oldState & ~(ERROR | ERROR_MASK);
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                return;
            }
        }
    }

    protected void hotStandby() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            newState = oldState | HOT_STANDBY;
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
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
                UndertowLogger.ROOT_LOGGER.infof("Node '%s' in error", jvmRoute);
                return;
            }
        }
    }

    /**
     * Mark a node in error. Mod_cluster has a threshold after which broken nodes get removed.
     *
     * @return
     */
    protected int healthCheckFailed() {
        int oldState, newState;
        for (;;) {
            oldState = this.state;
            if ((oldState & ERROR) != ERROR) {
                newState = oldState | ERROR;
                UndertowLogger.ROOT_LOGGER.infof("Node '%s' in error", jvmRoute);
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
        return allAreClear(state, ERROR | REMOVED | HOT_STANDBY) && connectionPool.available() == ProxyConnectionPool.AvailabilityType.AVAILABLE;
    }

    private class NodeConnectionPoolManager implements ConnectionPoolManager {
        //TODO: this whole thing...

        @Override
        public boolean canCreateConnection(int connections, ProxyConnectionPool proxyConnectionPool) {
            return true;
        }

        @Override
        public void queuedConnectionFailed(ProxyClient.ProxyTarget proxyTarget, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, long timeoutMills) {
            final ModClusterProxyTarget target = (ModClusterProxyTarget) proxyTarget;
            final Node node = target.findNode(exchange);
            if(node == null || node == Node.this) {
                callback.failed(exchange);
                return;
            }
            node.getConnectionPool().connect(proxyTarget, exchange, callback, timeoutMills, TimeUnit.MILLISECONDS, false);
        }

        @Override
        public int getProblemServerRetry() {
            return nodeConfig.getPing();
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

}
