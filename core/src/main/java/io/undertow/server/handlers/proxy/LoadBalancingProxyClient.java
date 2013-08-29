package io.undertow.server.handlers.proxy;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.AttachmentKey;
import io.undertow.util.ConduitFactory;
import io.undertow.util.Cookies;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.XnioExecutor;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    private final AttachmentKey<ClientConnection> clientAttachmentKey = AttachmentKey.create(ClientConnection.class);

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

    public synchronized LoadBalancingProxyClient addHost(final URI host) {
        Host h = new Host(host);
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
        removedHost.closed = true;
        return this;
    }

    @Override
    public void getConnection(HttpServerExchange exchange, ProxyCallback<ClientConnection> callback, long timeout, TimeUnit timeUnit) {
        ClientConnection existing = exchange.getConnection().getAttachment(clientAttachmentKey);
        if (existing != null && existing.isOpen()) {
            callback.completed(exchange, existing);
            return;
        }
        final Host host = selectHost(exchange);
        exchange.addResponseWrapper(new StickeySessionExchangeCompletionListener(host));
        connectToHost(host, exchange, callback);
    }

    private void connectToHost(final Host host, final HttpServerExchange exchange, final ProxyCallback<ClientConnection> callback) {
        client.connect(new ClientCallback<ClientConnection>() {
            @Override
            public void completed(final ClientConnection result) {
                host.problem = false;
                exchange.getConnection().putAttachment(clientAttachmentKey, result);
                exchange.getConnection().addCloseListener(new ServerConnection.CloseListener() {
                    @Override
                    public void closed(ServerConnection connection) {
                        IoUtils.safeClose(result);
                    }
                });
                callback.completed(exchange, result);

            }

            @Override
            public void failed(IOException e) {
                host.problem = true;
                scheduleFailedHostRetry(host, exchange);
                callback.failed(exchange);


            }
        }, host.uri, exchange.getIoThread(), exchange.getConnection().getBufferPool(), OptionMap.EMPTY);

    }

    private void scheduleFailedHostRetry(final Host host, final HttpServerExchange exchange) {
        exchange.getIoThread().executeAfter(new Runnable() {
            @Override
            public void run() {
                if (host.closed) {
                    return;
                }
                client.connect(new ClientCallback<ClientConnection>() {
                    @Override
                    public void completed(ClientConnection result) {
                        host.problem = false;
                        IoUtils.safeClose(result);
                    }

                    @Override
                    public void failed(IOException e) {
                        scheduleFailedHostRetry(host, exchange);
                    }
                }, host.uri, exchange.getIoThread(), exchange.getConnection().getBufferPool(), OptionMap.EMPTY);
            }
        }, problemServerRetry, TimeUnit.SECONDS);

    }

    protected Host selectHost(HttpServerExchange exchange) {
        Host sticky = findStickyHost(exchange);
        if (sticky != null) {
            return sticky;
        }
        Host[] hosts = this.hosts;
        int host = currentHost.incrementAndGet() % hosts.length;
        final int startHost = host; //if the all hosts have problems we come back to this one
        do {
            Host selected = hosts[host];
            if (!selected.problem) {
                return selected;
            }
            host = (host + 1) % hosts.length;
        } while (host != startHost);
        //they all have problems, just pick one
        return hosts[startHost];
    }

    protected Host findStickyHost(HttpServerExchange exchange) {
        Map<String, Cookie> cookies = exchange.getRequestCookies();
        for (String cookieName : sessionCookieNames) {
            Cookie sk = cookies.get(cookieName);
            if (sk != null) {
                StickeySessionData data = stickeySessionData.get(sk.getValue());
                if (data != null) {
                    if (data.host.closed || data.host.problem) {
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


    protected static class Host {

        private final URI uri;

        /**
         * flag that is set when a problem is detected with this host. It will be taken out of consideration
         * until the flag is cleared.
         * <p/>
         * The exception to this is if all flags are marked as problems, in which case it will be tried anyway
         */
        private volatile boolean problem;

        /**
         * Set to true when the host is removed from this load balancer
         */
        private volatile boolean closed;


        public Host(URI uri) {
            this.uri = uri;
        }
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

    private class StickeySessionExchangeCompletionListener implements ConduitWrapper<StreamSinkConduit> {

        private final Host host;

        private StickeySessionExchangeCompletionListener(Host host) {
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
