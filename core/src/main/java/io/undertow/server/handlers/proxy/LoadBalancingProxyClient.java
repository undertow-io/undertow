package io.undertow.server.handlers.proxy;

import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.AttachmentKey;
import io.undertow.util.CopyOnWriteMap;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.undertow.server.handlers.proxy.ProxyConnectionPool.AvailabilityType.AVAILABLE;
import static io.undertow.server.handlers.proxy.ProxyConnectionPool.AvailabilityType.FULL;
import static io.undertow.server.handlers.proxy.ProxyConnectionPool.AvailabilityType.PROBLEM;
import static org.xnio.IoUtils.safeClose;

/**
 * Initial implementation of a load balancing proxy client. This initial implementation is rather simplistic, and
 * will likely change.
 * <p/>
 *
 * @author Stuart Douglas
 */
public class LoadBalancingProxyClient implements ProxyClient {

    /**
     * The attachment key that is used to attach the proxy connection to the exchange.
     * <p/>
     * This cannot be static as otherwise a connection from a different client could be re-used.
     */
    private final AttachmentKey<ExclusiveConnectionHolder> exclusiveConnectionKey = AttachmentKey.create(ExclusiveConnectionHolder.class);


    /**
     * Time in seconds between retries for problem servers
     */
    private volatile int problemServerRetry = 10;

    private final Set<String> sessionCookieNames = new CopyOnWriteArraySet<String>();

    /**
     * The number of connections to create per thread
     */
    private volatile int connectionsPerThread = 10;

    /**
     * The hosts list.
     */
    private volatile Host[] hosts = {};

    private final AtomicInteger currentHost = new AtomicInteger(0);
    private final UndertowClient client;

    private final Map<String, Host> routes = new CopyOnWriteMap<String, Host>();

    private final ExclusivityChecker exclusivityChecker;

    private static final ProxyTarget PROXY_TARGET = new ProxyTarget() {
    };

    private final ConnectionPoolManager manager = new ConnectionPoolManager() {
        @Override
        public boolean canCreateConnection(int connections, ProxyConnectionPool proxyConnectionPool) {
            return connections < connectionsPerThread;
        }

        @Override
        public void queuedConnectionFailed(ProxyTarget proxyTarget, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, long timeoutMills) {
            getConnection(proxyTarget, exchange, callback, timeoutMills, TimeUnit.MILLISECONDS);
        }

        @Override
        public int getProblemServerRetry() {
            return problemServerRetry;
        }
    };

    public LoadBalancingProxyClient() {
        this(UndertowClient.getInstance());
    }

    public LoadBalancingProxyClient(UndertowClient client) {
        this(client, null);
    }

    public LoadBalancingProxyClient(ExclusivityChecker client) {
        this(UndertowClient.getInstance(), client);
    }

    public LoadBalancingProxyClient(UndertowClient client, ExclusivityChecker exclusivityChecker) {
        this.client = client;
        this.exclusivityChecker = exclusivityChecker;
        sessionCookieNames.add("JSESSIONID");
    }

    public LoadBalancingProxyClient addSessionCookieName(final String sessionCookieName) {
        sessionCookieNames.add(sessionCookieName);
        return this;
    }

    public LoadBalancingProxyClient removeSessionCookieName(final String sessionCookieName) {
        sessionCookieNames.remove(sessionCookieName);
        return this;
    }

    public LoadBalancingProxyClient setProblemServerRetry(int problemServerRetry) {
        this.problemServerRetry = problemServerRetry;
        return this;
    }

    public int getProblemServerRetry() {
        return problemServerRetry;
    }

    public int getConnectionsPerThread() {
        return connectionsPerThread;
    }

    public LoadBalancingProxyClient setConnectionsPerThread(int connectionsPerThread) {
        this.connectionsPerThread = connectionsPerThread;
        return this;
    }

    public synchronized LoadBalancingProxyClient addHost(final URI host) {
        return addHost(host, null);
    }

    public synchronized LoadBalancingProxyClient addHost(final URI host, String jvmRoute) {
        ProxyConnectionPool pool = new ProxyConnectionPool(manager, host, client);
        Host h = new Host(pool, jvmRoute, host);
        Host[] existing = hosts;
        Host[] newHosts = new Host[existing.length + 1];
        System.arraycopy(existing, 0, newHosts, 0, existing.length);
        newHosts[existing.length] = h;
        this.hosts = newHosts;
        if (jvmRoute != null) {
            this.routes.put(jvmRoute, h);
        }
        return this;
    }

    public synchronized LoadBalancingProxyClient removeHost(final URI uri) {
        int found = -1;
        Host[] existing = hosts;
        Host removedHost = null;
        for (int i = 0; i < existing.length; ++i) {
            if (existing[i].uri.equals(uri)) {
                found = i;
                removedHost = existing[i];
                break;
            }
        }
        if (found == -1) {
            return this;
        }
        Host[] newHosts = new Host[existing.length - 1];
        System.arraycopy(existing, 0, newHosts, 0, found);
        System.arraycopy(existing, found + 1, newHosts, found, existing.length - found - 1);
        this.hosts = newHosts;
        removedHost.connectionPool.close();
        if (removedHost.jvmRoute != null) {
            routes.remove(removedHost.jvmRoute);
        }
        return this;
    }

    @Override
    public ProxyTarget findTarget(HttpServerExchange exchange) {
        return PROXY_TARGET;
    }

    @Override
    public void getConnection(ProxyTarget target, HttpServerExchange exchange, final ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit) {
        final ExclusiveConnectionHolder holder = exchange.getConnection().getAttachment(exclusiveConnectionKey);
        if (holder != null && holder.connection.getConnection().isOpen()) {
            // Something has already caused an exclusive connection to be allocated so keep using it.
            callback.completed(exchange, holder.connection);
            return;
        }

        final Host host = selectHost(exchange);
        if (host == null) {
            callback.failed(exchange);
        } else {
            if (holder != null || (exclusivityChecker != null && exclusivityChecker.isExclusivityRequired(exchange))) {
                // If we have a holder, even if the connection was closed we now exclusivity was already requested so our client
                // may be assuming it still exists.
                host.connectionPool.connect(target, exchange, new ProxyCallback<ProxyConnection>() {

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
                host.connectionPool.connect(target, exchange, callback, timeout, timeUnit, false);
            }
        }
    }

    protected Host selectHost(HttpServerExchange exchange) {
        Host[] hosts = this.hosts;
        if (hosts.length == 0) {
            return null;
        }
        Host sticky = findStickyHost(exchange);
        if (sticky != null) {
            return sticky;
        }
        int host = currentHost.incrementAndGet() % hosts.length;

        final int startHost = host; //if the all hosts have problems we come back to this one
        Host full = null;
        Host problem = null;
        do {
            Host selected = hosts[host];
            ProxyConnectionPool.AvailabilityType available = selected.connectionPool.available();
            if (available == AVAILABLE) {
                return selected;
            } else if (available == FULL && full == null) {
                full = selected;
            } else if (available == PROBLEM && problem == null) {
                problem = selected;
            }
            host = (host + 1) % hosts.length;
        } while (host != startHost);
        if (full != null) {
            return full;
        }
        if (problem != null) {
            return problem;
        }
        //no available hosts
        return null;
    }

    protected Host findStickyHost(HttpServerExchange exchange) {
        Map<String, Cookie> cookies = exchange.getRequestCookies();
        for (String cookieName : sessionCookieNames) {
            Cookie sk = cookies.get(cookieName);
            if (sk != null) {
                int index = sk.getValue().indexOf('.');

                if (index == -1) {
                    continue;
                }
                String route = sk.getValue().substring(index + 1);
                index = route.indexOf('.');
                if (index != -1) {
                    route = route.substring(0, index);
                }
                return routes.get(route);
            }
        }
        return null;
    }

    protected static final class Host {
        final ProxyConnectionPool connectionPool;
        final String jvmRoute;
        final URI uri;

        private Host(ProxyConnectionPool connectionPool, String jvmRoute, URI uri) {
            this.connectionPool = connectionPool;
            this.jvmRoute = jvmRoute;
            this.uri = uri;
        }
    }

    private static class ExclusiveConnectionHolder {

        private ProxyConnection connection;

    }
}
