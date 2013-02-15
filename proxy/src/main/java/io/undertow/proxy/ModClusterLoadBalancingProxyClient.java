package io.undertow.proxy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.proxy.container.Node;
import io.undertow.proxy.container.NodeService;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.proxy.ConnectionPoolManager;
import io.undertow.server.handlers.proxy.ExclusivityChecker;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.server.handlers.proxy.ProxyConnectionPool;
import io.undertow.util.AttachmentKey;
import static org.xnio.IoUtils.safeClose;

public class ModClusterLoadBalancingProxyClient implements ProxyClient {

    /**
     * The attachment key that is used to attach the proxy connection to the exchange.
     * <p/>
     * This cannot be static as otherwise a connection from a different client could be re-used.
     */
    private final AttachmentKey<ExclusiveConnectionHolder> exclusiveConnectionKey = AttachmentKey
            .create(ExclusiveConnectionHolder.class);

    private static final ProxyTarget PROXY_TARGET = new ProxyTarget() {
    };
    private final ExclusivityChecker exclusivityChecker;
    private NodeService nodeservice = null;
    private final UndertowClient client;
    private volatile int connectionsPerThread = 10;
    private volatile int problemServerRetry = 10;
    private final ConnectionPoolManager manager = new ConnectionPoolManager() {
        @Override
        public boolean canCreateConnection(int connections, ProxyConnectionPool proxyConnectionPool) {
            return connections < connectionsPerThread;
        }

        @Override
        public void queuedConnectionFailed(ProxyTarget proxyTarget, HttpServerExchange exchange,
                ProxyCallback<ProxyConnection> callback, long timeoutMills) {
            getConnection(proxyTarget, exchange, callback, timeoutMills, TimeUnit.MILLISECONDS);
        }

        @Override
        public int getProblemServerRetry() {
            return problemServerRetry;
        }
    };

    public ModClusterLoadBalancingProxyClient() {
        this(UndertowClient.getInstance());
    }

    public ModClusterLoadBalancingProxyClient(UndertowClient client) {
        this(client, null);
    }

    public ModClusterLoadBalancingProxyClient(ExclusivityChecker client) {
        this(UndertowClient.getInstance(), client);
    }

    public ModClusterLoadBalancingProxyClient(UndertowClient client, ExclusivityChecker exclusivityChecker) {
        this.exclusivityChecker = exclusivityChecker;
        this.client = client;
    }

    @Override
    public ProxyTarget findTarget(HttpServerExchange exchange) {
        // TODO we probably needs a logic like in httpd (trans).
        return PROXY_TARGET;
    }

    @Override
    public void getConnection(ProxyTarget target, HttpServerExchange exchange, final ProxyCallback<ProxyConnection> callback,
            long timeout, TimeUnit timeUnit) {
        final ExclusiveConnectionHolder holder = exchange.getConnection().getAttachment(exclusiveConnectionKey);
        if (holder != null && holder.connection.getConnection().isOpen()) {
            // Something has already caused an exclusive connection to be
            // allocated so keep using it.
            callback.completed(exchange, holder.connection);
            return;
        }

        final Node node = selectNode(exchange);
        if (node == null) {
            callback.failed(exchange);
        } else {
            if (holder != null || (exclusivityChecker != null && exclusivityChecker.isExclusivityRequired(exchange))) {
                // If we have a holder, even if the connection was closed we now
                // exclusivity was already requested so our client
                // may be assuming it still exists.
                node.getConnectionPool().connect(target, exchange, new ProxyCallback<ProxyConnection>() {

                    @Override
                    public void failed(HttpServerExchange exchange) {
                        callback.failed(exchange);
                    }

                    @Override
                    public void completed(HttpServerExchange exchange, ProxyConnection result) {
                        if (holder != null) {
                            holder.connection = result;
                        } else {
                            final ExclusiveConnectionHolder newHolder = new ExclusiveConnectionHolder();
                            newHolder.connection = result;
                            ServerConnection connection = exchange.getConnection();
                            connection.putAttachment(exclusiveConnectionKey, newHolder);
                            connection.addCloseListener(new ServerConnection.CloseListener() {

                                @Override
                                public void closed(ServerConnection connection) {
                                    ClientConnection clientConnection = newHolder.connection.getConnection();
                                    if (clientConnection.isOpen()) {
                                        safeClose(clientConnection);
                                    }
                                }
                            });
                        }
                        callback.completed(exchange, result);
                    }
                }, timeout, timeUnit, true);
            } else {
                node.getConnectionPool().connect(target, exchange, callback, timeout, timeUnit, false);
            }
        }
    }

    private Node selectNode(HttpServerExchange exchange) {
        if (getNodeservice() == null)
            return null;
        Map<String, Cookie> map = exchange.getRequestCookies();
        String cookie = getNodeservice().getNodeByCookie(map);
        Node node = null;
        if (cookie != null) {
            // that should match a JVMRoute.
            node = getNodeservice().getNodeByCookie(cookie);
        } else {
            node = getNodeservice().getNode();
        }
        if (node != null) {
            // Make sure we have a connection Pool.
            ProxyConnectionPool pool = node.getConnectionPool();
            if (pool == null) {
                URI host;
                try {
                    host = new URI(node.getType() + "://" + node.getHostname() + ":" + node.getPort());
                } catch (URISyntaxException e) {
                    // TODO trace something?
                    return null;
                }
                pool = new ProxyConnectionPool(manager, host, client);
                node.setConnectionPool(pool);
            }

        }
        return node;
    }

    public NodeService getNodeservice() {
        return nodeservice;
    }

    public void setNodeservice(NodeService nodeservice) {
        this.nodeservice = nodeservice;
    }

    private static class ExclusiveConnectionHolder {

        private ProxyConnection connection;

    }

}
