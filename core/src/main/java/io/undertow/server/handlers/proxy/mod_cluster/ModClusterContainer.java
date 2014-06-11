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

package io.undertow.server.handlers.proxy.mod_cluster;

import io.undertow.UndertowLogger;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.proxy.ProxyConnectionPool;
import org.xnio.ssl.XnioSsl;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Container for all mod_proxy related things.
 *
 * @author Stuart Douglas
 */
public class ModClusterContainer {

    private final List<Balancer> balancers = new CopyOnWriteArrayList<>();
    private final List<Node> nodes = new CopyOnWriteArrayList<>();
    private final List<Context> contexts = new CopyOnWriteArrayList<>();
    private final List<Node> failedNodes = new CopyOnWriteArrayList<>();
    private final List<VHost> hosts = new CopyOnWriteArrayList<>();
    private final List<SessionId> sessionIds = Collections.synchronizedList(new ArrayList<SessionId>());
    private final Random random = new SecureRandom();
    private Timer timer;

    private final UndertowClient undertowClient;
    private final XnioSsl ssl;

    private volatile ModClusterLoadBalancingProxyClient proxyClient;

    public ModClusterContainer(UndertowClient undertowClient, XnioSsl ssl) {
        this.undertowClient = undertowClient;
        this.ssl = ssl;
    }

    public ModClusterContainer(UndertowClient undertowClient) {
        this(undertowClient, null);
    }

    public ModClusterContainer() {
        this(UndertowClient.getInstance(), null);
    }

    public synchronized void start() {
        timer = new Timer(true);


        startNewTimerTask(new NodeStatusChecker(), 500);
        // Start new thread for failed node health check
        startNewTimerTask(new HealthChecker(), 5000);
        startNewTimerTask(new MCMConfigBackgroundProcessor(), 5000);
        proxyClient = new ModClusterLoadBalancingProxyClient(null, this);
    }

    public synchronized void stop() {
        timer.cancel();
        proxyClient = null;
    }

    public ModClusterLoadBalancingProxyClient getProxyClient() {
        return proxyClient;
    }

    public Node findNode(final HttpServerExchange exchange) {
        for (Balancer balancer : balancers) {
            Map<String, Cookie> cookies = exchange.getRequestCookies();
            if (balancer.isStickySession()) {
                if (cookies.containsKey(balancer.getStickySessionCookie())) {
                    Node node = findNodeBySessionId(cookies.get(balancer.getStickySessionCookie()).getValue());
                    if (node != null && node.getConnectionPool().available() != ProxyConnectionPool.AvailabilityType.PROBLEM
                            && node.getNodeState().isNodeUp()) {
                        return node;
                    }
                }
                if (exchange.getPathParameters().containsKey(balancer.getStickySessionPath())) {
                    String id = exchange.getPathParameters().get(balancer.getStickySessionPath()).getFirst();
                    Node node = findNodeBySessionId(id);
                    if (node != null && node.getConnectionPool().available() != ProxyConnectionPool.AvailabilityType.PROBLEM
                            && node.getNodeState().isNodeUp()) {
                        return node;
                    }
                }
            }
        }
        return getNode();
    }

    /**
     * @param sessionId The full session id
     * @return The node, or <code>null</code>
     */
    public Node findNodeBySessionId(String sessionId) {
        int index = sessionId.indexOf('.');

        if (index == -1) {
            return null;
        }
        String route = sessionId.substring(index + 1);
        index = route.indexOf('.');
        if (index != -1) {
            route = route.substring(0, index);
        }
        for (Node node : nodes) {
            if (route.equals(node.getJvmRoute())) {
                return node;
            }
        }
        return null;
    }

    //OLD CODE

    /**
     * Create and start a new thread for the specified target task
     *
     * @param task
     */
    private void startNewTimerTask(final TimerTask task, long interval) {
        timer.schedule(task, interval, interval);
    }

    /**
     * @return the number of active nodes
     */
    public int getActiveNodes() {
        return this.nodes.size();
    }

    /**
     * Select a node randomly
     *
     * @param n the number of tries
     * @return a {@link Node}
     * @see #getNode()
     */
    private Node getNode(int n) {
        if (n >= this.nodes.size()) {
            return null;
        } else {
            int index = random.nextInt(this.nodes.size());
            Node node = this.nodes.get(index);
            return (node.getNodeState().isNodeUp() ? node : getNode(n + 1));
        }
    }

    public List<Balancer> getBalancers() {
        return balancers;
    }

    /**
     * Select a node for the specified {@code Request}
     *
     * @param sessionid
     * @return a node instance form the list of nodes
     */
    public Node getNodeBySessionid(String sessionid) {
        // URI decoding
        // String requestURI = request.decodedURI().toString();

        // TODO complete code here
        System.out.println("getNode: " + sessionid);

        return getNode();
    }

    /**
     *
     */
    public void printNodes() {
        if (this.nodes.isEmpty()) {
            UndertowLogger.ROOT_LOGGER.info("No nodes available");
            return;
        }
        StringBuilder sb = new StringBuilder("--> Available nodes : [");
        int i = 0;
        for (Node n : this.nodes) {
            sb.append(n.getNodeConfig().getHostname() + ":" + n.getNodeConfig().getPort());
            if ((i++) < this.nodes.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        UndertowLogger.ROOT_LOGGER.info(sb);
    }

    /**
     * Select a new node for the specified request and mark the failed node as unreachable
     *
     * @param sessionid
     * @param failedNode
     * @return
     */
    public Node getNodeBySessionid(String sessionid, Node failedNode) {
        if (failedNode != null) {
            // Set the node status to down
            UndertowLogger.ROOT_LOGGER.warn("The node [" + failedNode.getNodeConfig().getHostname() + ":" + failedNode.getNodeConfig().getPort() + "] is down");
            failedNode.getNodeState().setStatus(NodeState.NodeStatus.NODE_DOWN);
        }
        return getNodeBySessionid(sessionid);
    }

    public Node getNode(String jvmRoute) {
        for (Node nod : nodes) {
            if (nod.getJvmRoute().equals(jvmRoute)) {
                return nod;
            }
        }
        return null;
    }

    /* get the least loaded node according to the tablel values */
    public Node getNode() {
        Node nodeConfig = null;
        for (Node nod : nodes) {
            if (nod.getNodeState().getStatus() == NodeState.NodeStatus.NODE_DOWN)
                continue; // skip it.
            if (nodeConfig != null) {
                int status = ((nodeConfig.getNodeState().getElected() - nodeConfig.getNodeState().getOldelected()) * 1000) / nodeConfig.getNodeState().getLoad();
                int status1 = ((nod.getNodeState().getElected() - nod.getNodeState().getOldelected()) * 1000) / nod.getNodeState().getLoad();
                if (status1 > status)
                    nodeConfig = nod;
            } else
                nodeConfig = nod;
        }
        if (nodeConfig != null)
            nodeConfig.getNodeState().setElected(nodeConfig.getNodeState().getElected() + 1);
        return nodeConfig;
    }

    public void insertupdate(NodeConfig nodeConfig) {
        if (nodes.isEmpty()) {
            // TODO add the connection manager.
            nodes.add(new Node(nodeConfig, this, ssl, undertowClient));
        } else {
            int i = 1;
            Node replace = null;
            for (Node nod : nodes) {
                if (nod.getJvmRoute().equals(nodeConfig.getJvmRoute())) {
                    // replace it.
                    // TODO that is more tricky see mod_cluster C code.
                    replace = nod;
                    break;
                } else {
                    i++;
                }
            }
            if (replace != null) {
                replace.updateConfig(nodeConfig);
            } else {
                // TODO add the connection manager.
                nodes.add(new Node(nodeConfig, this, ssl, undertowClient));
            }
        }
    }

    public void insertupdate(Balancer balancer) {
        if (getBalancers().isEmpty()) {
            getBalancers().add(balancer);
        } else {
            for (Balancer bal : getBalancers()) {
                if (bal.getName().equals(balancer.getName())) {
                    // replace it.
                    // TODO that is more tricky see mod_cluster C code.
                    getBalancers().remove(bal);
                    getBalancers().add(balancer);
                    break; // Done
                }
            }
        }
    }

    public long insertupdate(VHost host) {
        int i = 1;
        if (hosts.isEmpty()) {
            host.setId(i);
            hosts.add(host);
            return 1;
        } else {
            for (VHost hos : hosts) {
                if (hos.getJVMRoute().equals(host.getJVMRoute())
                        && isSame(host.getAliases(), hos.getAliases())) {
                    return hos.getId();
                }
                i++;
            }
        }
        host.setId(i);
        hosts.add(host);
        return i;
    }

    private boolean isSame(List<String> aliases, List<String> aliases2) {
        if (aliases.size() != aliases2.size())
            return false;
        for (String host : aliases)
            if (!aliases.contains(host))
                return false;
        return true;
    }

    public void insertupdate(Context context) {
        if (contexts.isEmpty()) {
            contexts.add(context);
            return;
        } else {
            for (Context con : contexts) {
                if (context.getJvmRoute().equals(con.getJvmRoute())
                        && context.getHostid() == con.getHostid()
                        && context.getPath().equals(con.getPath())) {
                    // update the status.
                    con.setStatus(context.getStatus());
                    return;
                }
            }
            contexts.add(context);
        }
    }

    public void checkHealthNode() {
        for (Node nod : nodes) {
            if (nod.getNodeState().getElected() == nod.getNodeState().getOldelected()) {
                // nothing change bad
                // TODO and the CPING/CPONG
            } else {
                nod.getNodeState().setOldelected(nod.getNodeState().getElected());
            }
        }
    }

    /*
     * remove the context and the corresponding host if that is last context of the host.
     */

    public void remove(Context context, VHost host) {
        for (Context con : contexts) {
            if (context.getJvmRoute().equals(con.getJvmRoute())
                    && isSame(getHostById(con.getHostid()).getAliases(), host.getAliases())
                    && context.getPath().equals(con.getPath())) {
                contexts.remove(con);
                removeEmptyHost(con.getHostid());
                return;
            }

        }
    }

    private void removeEmptyHost(long hostid) {
        boolean remove = true;
        for (Context con : contexts) {
            if (con.getHostid() == hostid) {
                remove = false;
                break;
            }
        }
        if (remove)
            hosts.remove(getHostById(hostid));
    }

    private VHost getHostById(long hostid) {
        for (VHost hos : hosts) {
            if (hos.getId() == hostid)
                return hos;
        }
        return null;
    }

    /*
     * Remove the node, host, context corresponding to jvmRoute.
     */
    public void removeNode(String jvmRoute) {
        List<Context> remcons = new ArrayList<>();
        for (Context con : contexts) {
            if (con.getJvmRoute().equals(jvmRoute))
                remcons.add(con);
        }
        for (Context con : remcons)
            contexts.remove(con);

        List<VHost> remhosts = new ArrayList<>();
        for (VHost hos : hosts) {
            if (hos.getJVMRoute().equals(jvmRoute))
                remhosts.add(hos);
        }
        for (VHost hos : remhosts)
            hosts.remove(hos);

        List<Node> remnodes = new ArrayList<>();
        for (Node nod : nodes) {
            if (nod.getJvmRoute().equals(jvmRoute))
                remnodes.add(nod);
        }
        for (Node nod : remnodes)
            nodes.remove(nod);
    }

    public List<SessionId> getSessionIds() {
        return sessionIds;
    }

    /*
     * Count the number of sessionid corresponding to the node.
     */
    public String getJVMRouteSessionCount(String jvmRoute) {
        int i = 0;
        for (SessionId s : this.sessionIds) {
            if (s.getJmvRoute().equals(jvmRoute))
                i++;
        }
        return "" + i;
    }

    void scheduleTask(TimerTask task, int interval) {
        timer.schedule(task, interval, interval);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Context> getContexts() {
        return contexts;
    }

    public List<VHost> getHosts() {
        return hosts;
    }

    public long getNodeId(String jvmRoute) {
        Node node = getNode(jvmRoute);
        if(node != null) {
            return node.getId();
        }
        return -1;
    }


    protected class MCMConfigBackgroundProcessor extends TimerTask {

        @Override
        public void run() {
            checkHealthNode();
        }

    }

    /**
     * {@code HealthChecker}
     * <p/>
     * Created on Sep 18, 2012 at 3:46:36 PM
     *
     * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
     */
    private class HealthChecker extends TimerTask {

        @Override
        public void run() {
            List<Node> tmp = new ArrayList<>();
            if (failedNodes.isEmpty()) {
                return;
            }
            UndertowLogger.ROOT_LOGGER.debug("Starting health check for previously failed nodes");
            for (Node nodeConfig : failedNodes) {
                if (checkHealth(nodeConfig)) {
                    nodeConfig.getNodeState().setStatus(NodeState.NodeStatus.NODE_UP);
                    tmp.add(nodeConfig);
                }
            }

            if (tmp.isEmpty()) {
                return;
            }

            nodes.addAll(tmp);

            failedNodes.removeAll(tmp);

        }

        /**
         * Check the health of the failed node
         *
         * @param node
         * @return <tt>true</tt> if the node is reachable else <tt>false</tt>
         */
        public boolean checkHealth(Node node) {
            if (node == null) {
                return false;
            }
            boolean ok = false;
            // TODO we should use the connectionPool instead.
            java.net.Socket s = null;
            try {
                s = new java.net.Socket(node.getNodeConfig().getHostname(), node.getNodeConfig().getPort());
                s.setSoLinger(true, 0);
                ok = true;
            } catch (Exception e) {
                // Ignore
            } finally {
                if (s != null) {
                    try {
                        s.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            return ok;
        }
    }

    /**
     * {@code NodeStatusChecker}
     * <p/>
     * Created on Sep 18, 2012 at 3:49:56 PM
     *
     * @author <a href="mailto:nbenothm@redhat.com">Nabil Benothman</a>
     */
    private class NodeStatusChecker extends TimerTask {

        @Override
        public void run() {
            List<Node> tmp = new ArrayList<>();
            try {
                // Retrieve nodes with status "DOWN"
                for (Node n : nodes) {
                    if (n.getNodeState().isNodeDown()) {
                        tmp.add(n);
                    }
                }

                if (tmp.isEmpty()) {
                    return;
                }
                // Remove failed nodes from the list of nodes
                nodes.removeAll(tmp);
                // Add selected nodes to the list of failed nodes
                failedNodes.addAll(tmp);
                tmp.clear();

                // Retrieve nodes with status "UP"
                for (Node n : failedNodes) {
                    if (n.getNodeState().isNodeUp()) {
                        tmp.add(n);
                    }
                }

                if (tmp.isEmpty()) {
                    return;
                }
                // Remove all healthy nodes from the list of failed nodes
                failedNodes.removeAll(tmp);
                // Add selected nodes to the list of healthy nodes
                nodes.addAll(tmp);
                tmp.clear();

                // printNodes();
            } catch (Throwable e) {
                e.printStackTrace();
            }

        }
    }

}
