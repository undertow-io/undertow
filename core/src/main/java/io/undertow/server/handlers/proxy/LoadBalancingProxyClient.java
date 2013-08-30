package io.undertow.server.handlers.proxy;

import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Cookies;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.xnio.XnioExecutor;
import org.xnio.conduits.StreamSinkConduit;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.undertow.server.handlers.proxy.Host.AvailabilityType.AVAILABLE;
import static io.undertow.server.handlers.proxy.Host.AvailabilityType.CLOSED;
import static io.undertow.server.handlers.proxy.Host.AvailabilityType.FULL;
import static io.undertow.server.handlers.proxy.Host.AvailabilityType.PROBLEM;

/**
 * Initial implementation of a load balancing proxy client. This initial implementation is rather simplistic, and
 * will likely change.
 * <p/>
 * TODO stickey sessions don't work 100% when proxying to multiple applications, as it is possible that they will recieve
 * a different assigned host for each applications session, and the session is tied to the exchanges connection.
 *
 * @author Stuart Douglas
 */
public class LoadBalancingProxyClient implements ProxyClient {

    /**
     * Time in seconds between retries for problem servers
     */
    private volatile int problemServerRetry = 10;

    /**
     * Time to remember sticky session lifetime, in seconds.
     */
    private volatile int stickeySessionLifetime = 100 * 60;

    private final Set<String> sessionCookieNames = new CopyOnWriteArraySet<String>();

    private final Map<String, StickeySessionData> stickeySessionData = new ConcurrentHashMap<String, StickeySessionData>();

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

    public int getStickeySessionLifetime() {
        return stickeySessionLifetime;
    }

    public LoadBalancingProxyClient setStickeySessionLifetime(int stickeySessionLifetime) {
        this.stickeySessionLifetime = stickeySessionLifetime;
        return this;
    }

    public int getConnectionsPerThread() {
        return connectionsPerThread;
    }

    public LoadBalancingProxyClient setConnectionsPerThread(int connectionsPerThread) {
        this.connectionsPerThread = connectionsPerThread;
        return this;
    }

    public synchronized LoadBalancingProxyClient addHost(final URI host) {
        Host h = new Host(this, host, client);
        Host[] existing = hosts;
        Host[] newHosts = new Host[existing.length + 1];
        System.arraycopy(existing, 0, newHosts, 0, existing.length);
        newHosts[existing.length] = h;
        this.hosts = newHosts;
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
        return this;
    }

    @Override
    public void getConnection(HttpServerExchange exchange, ProxyCallback<ClientConnection> callback, long timeout, TimeUnit timeUnit) {
        final Host host = selectHost(exchange);
        if(host == null) {
            callback.failed(exchange);
        } else {
            exchange.addResponseWrapper(new StickeySessionWrapper(host));
            host.connect(exchange, callback, timeout, timeUnit);
        }
    }

    protected Host selectHost(HttpServerExchange exchange) {
        Host[] hosts = this.hosts;
        if(hosts.length == 0) {
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
                StickeySessionData data = stickeySessionData.get(sk.getValue());
                if (data != null) {
                    Host.AvailabilityType state = data.host.availible();
                    if (state == PROBLEM || state == CLOSED) {
                        stickeySessionData.remove(sk.getValue());
                    } else {
                        data.bumpTimeout(exchange.getIoThread());
                        return data.host;
                    }
                }
            }
        }
        return null;
    }


    protected class StickeySessionData implements Runnable {

        private final String sessionId;
        private final Host host;
        private final AtomicReference<XnioExecutor.Key> timeoutKey = new AtomicReference<XnioExecutor.Key>();

        public StickeySessionData(String sessionId, Host host) {
            this.sessionId = sessionId;
            this.host = host;
        }

        void bumpTimeout(final XnioExecutor executor) {
            XnioExecutor.Key current = timeoutKey.get();
            if (current != null) {
                current.remove();
            }
            XnioExecutor.Key newKey = executor.executeAfter(this, stickeySessionLifetime, TimeUnit.SECONDS);
            if (!timeoutKey.compareAndSet(current, newKey)) {
                newKey.remove();
            }

        }

        @Override
        public void run() {
            stickeySessionData.remove(sessionId);
        }
    }

    private class StickeySessionWrapper implements ConduitWrapper<StreamSinkConduit> {

        private final Host host;

        private StickeySessionWrapper(Host host) {
            this.host = host;
        }

        @Override
        public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
            //we don't actually want to wrap the response, just inspect the exchange and look for a session cookie

            HeaderValues cookies = exchange.getResponseHeaders().get(Headers.SET_COOKIE);
            if (cookies != null) {
                for (String cookieHeader : cookies) {
                    try {
                        Cookie cookie = Cookies.parseSetCookieHeader(cookieHeader);
                        if (sessionCookieNames.contains(cookie.getName())) {
                            if (!stickeySessionData.containsKey(cookie.getValue())) {
                                final StickeySessionData data = new StickeySessionData(cookie.getValue(), host);
                                stickeySessionData.put(cookie.getValue(), data);
                                data.bumpTimeout(exchange.getIoThread());
                            }
                        }
                    } catch (IllegalArgumentException ignore) {
                        //if we can't parse the cookie for some reason
                    }
                }
            }
            return factory.create();
        }
    }

}
