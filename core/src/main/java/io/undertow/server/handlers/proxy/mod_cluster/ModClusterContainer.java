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

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import io.undertow.UndertowLogger;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.cache.LRUCache;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Headers;
import io.undertow.util.PathMatcher;
import org.xnio.Pool;
import org.xnio.XnioIoThread;
import org.xnio.ssl.XnioSsl;

/**
 * @author Stuart Douglas
 * @author Emanuel Muckenhuber
 */
class ModClusterContainer {

    // The configured balancers
    private final ConcurrentMap<String, Balancer> balancers = new CopyOnWriteMap<>();

    // The available nodes
    private final ConcurrentMap<String, Node> nodes = new CopyOnWriteMap<>();

    // virtual-host > per context balancing table
    private final ConcurrentMap<String, VirtualHost> hosts = new CopyOnWriteMap<>();

    // Map of removed jvmRoutes to failover domain
    private final LRUCache<String, String> failoverDomains = new LRUCache<>(100, 5 * 60 * 1000);

    private final XnioSsl xnioSsl;
    private final UndertowClient client;
    private final ProxyClient proxyClient;
    private final ModCluster modCluster;
    private final long removeBrokenNodesThreshold;

    ModClusterContainer(final ModCluster modCluster, final XnioSsl xnioSsl, final UndertowClient client) {
        this.xnioSsl = xnioSsl;
        this.client = client;
        this.modCluster = modCluster;
        this.proxyClient = new ModClusterProxyClient(null, this);
        this.removeBrokenNodesThreshold = removeThreshold(modCluster.getHealthCheckInterval(), modCluster.getRemoveBrokenNodes());
    }

    String getServerID() {
        return modCluster.getServerID();
    }

    UndertowClient getClient() {
        return client;
    }

    XnioSsl getXnioSsl() {
        return xnioSsl;
    }

    /**
     * Get the proxy client.
     *
     * @return the proxy client
     */
    public ProxyClient getProxyClient() {
        return proxyClient;
    }

    Collection<Balancer> getBalancers() {
        return Collections.unmodifiableCollection(balancers.values());
    }

    Collection<Node> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    Node getNode(final String jvmRoute) {
        return nodes.get(jvmRoute);
    }

    /**
     * Get the mod cluster proxy target.
     *
     * @param exchange the http exchange
     * @return
     */
    public ModClusterProxyTarget findTarget(final HttpServerExchange exchange) {
        // There is an option to disable the virtual host check, probably a default virtual host
        final PathMatcher.PathMatch<VirtualHost.HostEntry> entry = mapVirtualHost(exchange);
        if (entry == null) {
            return null;
        }
        for (final Balancer balancer : balancers.values()) {
            final Map<String, Cookie> cookies = exchange.getRequestCookies();
            if (balancer.isStickySession()) {
                if (cookies.containsKey(balancer.getStickySessionCookie())) {
                    final String jvmRoute = getJVMRoute(cookies.get(balancer.getStickySessionCookie()).getValue());
                    if (jvmRoute != null) {
                        return new ModClusterProxyTarget.ExistingSessionTarget(jvmRoute, entry.getValue(), this, balancer.isStickySessionForce());
                    }
                }
                if (exchange.getPathParameters().containsKey(balancer.getStickySessionPath())) {
                    final String id = exchange.getPathParameters().get(balancer.getStickySessionPath()).getFirst();
                    final String jvmRoute = getJVMRoute(id);
                    if (jvmRoute != null) {
                        return new ModClusterProxyTarget.ExistingSessionTarget(jvmRoute, entry.getValue(), this, balancer.isStickySessionForce());
                    }
                }
            }
        }
        return new ModClusterProxyTarget.BasicTarget(entry.getValue(), this);
    }

    /**
     * Register a new node.
     *
     * @param config            the node configuration
     * @param balancerConfig    the balancer configuration
     * @param ioThread          the associated I/O thread
     * @param bufferPool        the buffer pool
     * @return whether the node could be created or not
     */
    public synchronized boolean addNode(final NodeConfig config, final Balancer.BalancerBuilder balancerConfig, final XnioIoThread ioThread, final Pool<ByteBuffer> bufferPool) {

        final String jvmRoute = config.getJvmRoute();
        final Node existing = nodes.get(jvmRoute);
        if (existing != null) {
            if (config.getConnectionURI().equals(existing.getNodeConfig().getConnectionURI())) {
                // TODO better check if they are the same
                existing.resetState();
                return true;
            } else {
                existing.markRemoved();
                removeNode(existing);
                if (!existing.isInErrorState()) {
                    return false; // replies with MNODERM error
                }
            }
        }

        final String balancerRef = config.getBalancer();
        Balancer balancer = balancers.get(balancerRef);
        if (balancer == null) {
            // TODO compare balancer configs, if they are not equal log a warning?
            balancer = balancerConfig.build();
            balancers.put(balancerRef, balancer);
        }
        final Node node = new Node(config, balancer, ioThread, bufferPool, this);
        nodes.put(jvmRoute, node);
        // Remove from the failover groups
        failoverDomains.remove(node.getJvmRoute());
        UndertowLogger.ROOT_LOGGER.infof("registering node %s, connection: %s", jvmRoute, config.getConnectionURI());
        return true;
    }

    /**
     * Management command enabling all contexts on the given node.
     *
     * @param jvmRoute    the jvmRoute
     * @return
     */
    public synchronized boolean enableNode(final String jvmRoute) {
        final Node node = nodes.get(jvmRoute);
        if (node != null) {
            for (final Context context : node.getContexts()) {
                context.enable();
            }
            return true;
        }
        return false;
    }

    /**
     * Management command disabling all contexts on the given node.
     *
     * @param jvmRoute    the jvmRoute
     * @return
     */
    public synchronized boolean disableNode(final String jvmRoute) {
        final Node node = nodes.get(jvmRoute);
        if (node != null) {
            for (final Context context : node.getContexts()) {
                context.disable();
            }
            return true;
        }
        return false;
    }

    /**
     * Management command stopping all contexts on the given node.
     *
     * @param jvmRoute    the jvmRoute
     * @return
     */
    public synchronized boolean stopNode(final String jvmRoute) {
        final Node node = nodes.get(jvmRoute);
        if (node != null) {
            for (final Context context : node.getContexts()) {
                context.stop();
            }
            return true;
        }
        return false;
    }

    /**
     * Remove a node.
     *
     * @param jvmRoute the jvmRoute
     * @return the removed node
     */
    public synchronized Node removeNode(final String jvmRoute) {
        final Node node = nodes.get(jvmRoute);
        if (node != null) {
            removeNode(node);
        }
        return node;
    }

    protected synchronized void removeNode(final Node node) {
        final String jvmRoute = node.getJvmRoute();
        node.markRemoved();
        if (nodes.remove(jvmRoute, node)) {
            UndertowLogger.ROOT_LOGGER.infof("removing node %s", jvmRoute);
            node.markRemoved();
            for (final Context context : node.getContexts()) {
                removeContext(context.getPath(), node, context.getVirtualHosts());
            }
            final String domain = node.getNodeConfig().getDomain();
            if (domain != null) {
                failoverDomains.add(node.getJvmRoute(), domain);
            }
            final String balancerName = node.getBalancer().getName();
            for (final Node other : nodes.values()) {
                if (other.getBalancer().getName().equals(balancerName)) {
                    return;
                }
            }
            balancers.remove(balancerName);
        }
    }

    /**
     * Register a web context. If the web context already exists, just enable it.
     *
     * @param contextPath the context path
     * @param jvmRoute    the jvmRoute
     * @param aliases     the virtual host aliases
     */
    public synchronized boolean enableContext(final String contextPath, final String jvmRoute, final List<String> aliases) {
        final Node node = nodes.get(jvmRoute);
        if (node != null) {
            Context context = node.getContext(contextPath, aliases);
            if (context == null) {
                context = node.registerContext(contextPath, aliases);
                UndertowLogger.ROOT_LOGGER.infof("registering context %s, for node %s, with aliases %s", contextPath, jvmRoute, aliases);
                for (final String alias : aliases) {
                    VirtualHost virtualHost = hosts.get(alias);
                    if (virtualHost == null) {
                        virtualHost = new VirtualHost();
                        hosts.put(alias, virtualHost);
                    }
                    virtualHost.registerContext(contextPath, jvmRoute, context);
                }
            }
            context.enable();
            return true;
        }
        return false;
    }

    synchronized boolean disableContext(final String contextPath, final String jvmRoute, List<String> aliases) {
        final Node node = nodes.get(jvmRoute);
        if (node != null) {
            node.disableContext(contextPath, aliases);
            return true;
        }
        return false;
    }

    synchronized int stopContext(final String contextPath, final String jvmRoute, List<String> aliases) {
        final Node node = nodes.get(jvmRoute);
        if (node != null) {
            return node.stopContext(contextPath, aliases);
        }
        return -1;
    }

    synchronized boolean removeContext(final String contextPath, final String jvmRoute, List<String> aliases) {
        final Node node = nodes.get(jvmRoute);
        if (node != null) {
            return removeContext(contextPath, node, aliases);
        }
        return false;
    }

    public synchronized boolean removeContext(final String contextPath, final Node node, List<String> aliases) {
        if (node == null) {
            return false;
        }
        final String jvmRoute = node.getJvmRoute();
        UndertowLogger.ROOT_LOGGER.infof("unregistering context '%s' from node '%s'", contextPath, jvmRoute);
        final Context context = node.removeContext(contextPath, aliases);
        if (context == null) {
            return false;
        }
        context.stop();
        for (final String alias : context.getVirtualHosts()) {
            final VirtualHost virtualHost = hosts.get(alias);
            if (virtualHost != null) {
                virtualHost.removeContext(contextPath, jvmRoute, context);
                if (virtualHost.isEmpty()) {
                    hosts.remove(alias);
                }
            }
        }
        return true;
    }

    /**
     * Check the health of all registered nodes
     */
    void checkHealth() {
        for (final Node node : nodes.values()) {
            node.checkHealth(removeBrokenNodesThreshold);
        }
    }

    /**
     * Find a new node handling this request.
     *
     * @param entry the resolved virtual host entry
     * @return the node, {@code null} if no node could be found
     */
    Node findNewNode(final VirtualHost.HostEntry entry) {
        return electNode(entry.getContexts(), false, null);
    }

    /**
     * Try to find a failover node within the same load balancing group.
     *
     * @oaram entry      the resolved virtual host entry
     * @param domain     the load balancing domain, if known
     * @param jvmRoute   the original jvmRoute
     * @return
     */
    Node findFailoverNode(final VirtualHost.HostEntry entry, final String domain, final String jvmRoute, final boolean forceStickySession) {
        String failOverDomain = null;
        if (domain == null) {
            final Node node = nodes.get(jvmRoute);
            if (node != null) {
                failOverDomain = node.getNodeConfig().getDomain();
            }
            if (failOverDomain == null) {
                failOverDomain = failoverDomains.get(jvmRoute);
            }
        } else {
            failOverDomain = domain;
        }
        final Collection<Context> contexts = entry.getContexts();
        if (failOverDomain != null) {
            final Node node = electNode(contexts, true, failOverDomain);
            if (node != null) {
                return node;
            }
        }
        if (forceStickySession) {
            return null;
        } else {
            return electNode(contexts, false, null);
        }
    }

    /**
     * Map a request to virtual host.
     *
     * @param exchange    the http exchange
     * @return
     */
    private PathMatcher.PathMatch<VirtualHost.HostEntry> mapVirtualHost(final HttpServerExchange exchange) {
        final String hostName = exchange.getRequestHeaders().getFirst(Headers.HOST);
        if (hostName != null) {
            final String context = exchange.getRelativePath();
            VirtualHost host = hosts.get(hostName);
            if (host == null) {
                int i = hostName.indexOf(":"); // Remove the port from the host
                if (i > 0) {
                    host = hosts.get(hostName.substring(0, i));
                    if (host == null) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            return host.match(context);
        }
        return null;
    }

    static String getJVMRoute(final String sessionId) {
        int index = sessionId.indexOf('.');
        if (index == -1) {
            return null;
        }
        String route = sessionId.substring(index + 1);
        index = route.indexOf('.');
        if (index != -1) {
            route = route.substring(0, index);
        }
        return route;
    }

    static Node electNode(final Iterable<Context> contexts, final boolean existingSession, final String domain) {
        Node candidate = null;
        boolean candidateHotStandby = false;
        for (Context context : contexts) {
            // Skip disabled contexts
            if (context.checkAvailable(existingSession)) {
                final Node node = context.getNode();
                final boolean hotStandby = node.isHotStandby();
                // Check that we only failover in the domain
                if (domain != null && !domain.equals(node.getNodeConfig().getDomain())) {
                    continue;
                }
                if (candidate != null) {
                    // Check if the nodes are in hot-standby
                    if (candidateHotStandby) {
                        if (hotStandby) {
                            if (candidate.getElectedDiff() > node.getElectedDiff()) {
                                candidate = node;
                            }
                        } else {
                            candidate = node;
                            candidateHotStandby = hotStandby;
                        }
                    } else if (hotStandby) {
                        continue;
                    } else {
                        // Normal election process
                        final int lbStatus1 = candidate.getLoadStatus();
                        final int lbStatus2 = node.getLoadStatus();
                        if (lbStatus1 > lbStatus2) {
                            candidate = node;
                            candidateHotStandby = false;
                        }
                    }
                } else {
                    candidate = node;
                    candidateHotStandby = hotStandby;
                }
            }
        }
        if (candidate != null) {
            candidate.elected(); // We have a winner!
        }
        return candidate;
    }

    static long removeThreshold(final long healthChecks, final long removeBrokenNodes) {
        if (healthChecks > 0 && removeBrokenNodes > 0) {
            final long threshold = removeBrokenNodes / healthChecks;
            if (threshold > 1000) {
                return 1000;
            } else if (threshold < 1) {
                return 1;
            } else {
                return threshold;
            }
        } else {
            return -1;
        }
    }

}
