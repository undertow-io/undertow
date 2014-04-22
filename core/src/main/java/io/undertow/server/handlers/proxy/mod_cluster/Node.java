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

import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.ConnectionPoolManager;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.server.handlers.proxy.ProxyConnectionPool;
import org.xnio.ssl.XnioSsl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a node, as identified by the JVM route.
 *
 * This is broken into two parts, the config which is represented by an immutable config object,
 * and the current state which is mutable object that represents the current state of the node.
 *
 *
 * @author Stuart Douglas
 */
public class Node {

    private static final AtomicInteger counter = new AtomicInteger(0);

    private final int id;
    private final String jvmRoute;
    private final ModClusterContainer container;
    private final ConnectionPoolManager connectionPoolManager;
    private final XnioSsl xnioSsl;
    private final UndertowClient client;
    private final NodeState nodeState;

    private volatile NodeConfig nodeConfig;
    private volatile ProxyConnectionPool connectionPool;

    Node(final NodeConfig nodeConfig, ModClusterContainer container, XnioSsl xnioSsl, UndertowClient client) {
        this.nodeConfig = nodeConfig;
        this.nodeState = new NodeState();
        this.xnioSsl = xnioSsl;
        this.client = client;
        id = counter.incrementAndGet();
        this.jvmRoute = nodeConfig.getJvmRoute();
        this.container = container;
        this.connectionPoolManager = new NodeConnectionPoolManager();
    }

    synchronized void updateConfig(NodeConfig config) {
        //TODO: do more stuff
        ProxyConnectionPool pool = connectionPool;
        this.connectionPool = null;
        pool.close();
        this.nodeConfig = config;
    }

    public NodeState getNodeState() {
        return nodeState;
    }

    public NodeConfig getNodeConfig() {
        return nodeConfig;
    }

    public int getId() {
        return id;
    }

    public ProxyConnectionPool getConnectionPool() {
        if(connectionPool == null) {
            synchronized (this) {
                if(connectionPool == null) {
                    try {
                        connectionPool = new ProxyConnectionPool(connectionPoolManager, new URI(nodeConfig.getType(), null, nodeConfig.getHostname(), nodeConfig.getPort(), "/", "", ""), xnioSsl, client);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return connectionPool;
    }

    /**
     * @param pos the position of the node in the list
     * @return the global information about the node
     */
    public String getInfos(int pos) {
        return new StringBuilder("Node: [" + pos + "],Name: ").append(nodeConfig.getJvmRoute()).append("Balancer: ").append(nodeConfig.getBalancer())
                .append(",LBGroup: ").append(nodeConfig.getDomain()).append(",Host: ").append(nodeConfig.getHostname()).append(",Port: ")
                .append(nodeConfig.getPort()).append(",Type: ").append(nodeConfig.getType()).append(",Flushpackets: ")
                .append((nodeConfig.isFlushPackets() ? "On" : "Off")).append(",Flushwait: ").append(nodeConfig.getFlushwait()).append(",Ping: ")
                .append(nodeConfig.getPing()).append(",Smax: ").append(nodeConfig.getSmax()).append(",Ttl: ").append(nodeConfig.getTtl()).append(",Elected: ")
                .append(nodeState.getElected()).append(",Read: ").append(nodeState.getRead()).append(",Transfered: ").append(nodeState.getTransfered())
                .append(",Connected: ").append(nodeState.getConnected()).append(",Load: ").append(nodeState.getLoad()).append("\n").toString();
    }

    @Override
    public String toString() {
        // TODO complete node name
        StringBuilder sb = new StringBuilder("Node: [x:y]").append("], Balancer: ").append(nodeConfig.getBalancer())
                .append(", JVMRoute: ").append(nodeConfig.getJvmRoute()).append(", Domain: [").append(nodeConfig.getDomain()).append("], Host: ")
                .append(nodeConfig.getHostname()).append(", Port: ").append(nodeConfig.getPort()).append(", Type: ").append(nodeConfig.getType())
                .append(", flush-packets: ").append(nodeConfig.isFlushPackets() ? 1 : 0).append(", flush-wait: ").append(nodeConfig.getFlushwait())
                .append(", Ping: ").append(nodeConfig.getPing()).append(", smax: ").append(nodeConfig.getSmax()).append(", TTL: ").append(nodeConfig.getTtl())
                .append(", Timeout: ").append(nodeConfig.getTimeout());

        return sb.toString();
    }

    public String getJvmRoute() {
        return jvmRoute;
    }

    private class NodeConnectionPoolManager implements ConnectionPoolManager {
        //TODO: this whole thing...

        @Override
        public boolean canCreateConnection(int connections, ProxyConnectionPool proxyConnectionPool) {
            return true;
        }

        @Override
        public void queuedConnectionFailed(ProxyClient.ProxyTarget proxyTarget, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, long timeoutMills) {
            //TODO: ?????
            Node node = container.findNode(exchange);
            if(node == null || node == Node.this) {
                callback.failed(exchange);
                return;
            }
            node.getConnectionPool().connect(proxyTarget, exchange, callback, timeoutMills, TimeUnit.MILLISECONDS, false);
        }

        @Override
        public int getProblemServerRetry() {
            return nodeConfig.getPing();//TODO????
        }
    }
}
