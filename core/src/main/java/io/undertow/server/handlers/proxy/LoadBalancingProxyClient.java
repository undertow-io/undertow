package io.undertow.server.handlers.proxy;

import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.CopyOnWriteMap;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.undertow.server.handlers.proxy.Host.AvailabilityType.AVAILABLE;
import static io.undertow.server.handlers.proxy.Host.AvailabilityType.FULL;
import static io.undertow.server.handlers.proxy.Host.AvailabilityType.PROBLEM;

/**
 * Initial implementation of a load balancing proxy client. This initial implementation is rather simplistic, and
 * will likely change.
 * <p/>
 *
 * @author Stuart Douglas
 */
public class LoadBalancingProxyClient implements ProxyClient {

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

    private static final ProxyTarget PROXY_TARGET = new ProxyTarget() {};

    public LoadBalancingProxyClient() {
        this(UndertowClient.getInstance());
    }

    public LoadBalancingProxyClient(UndertowClient client) {
        this.client = client;
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
        Host h = new Host(this, host, jvmRoute, client);
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
            if (existing[i].getUri().equals(uri)) {
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
        removedHost.close();
        if (removedHost.getJvmRoute() != null) {
            routes.remove(removedHost.getJvmRoute());
        }
        return this;
    }

    @Override
    public ProxyTarget findTarget(HttpServerExchange exchange) {
        return PROXY_TARGET;
    }

    @Override
    public void getConnection(ProxyTarget target, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit) {
        final Host host = selectHost(exchange);
        if (host == null) {
            callback.failed(exchange);
        } else {
            host.connect(target, exchange, callback, timeout, timeUnit);
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
            Host.AvailabilityType availble = selected.availible();
            if (availble == AVAILABLE) {
                return selected;
            } else if (availble == FULL && full == null) {
                full = selected;
            } else if (availble == PROBLEM && problem == null) {
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
}
