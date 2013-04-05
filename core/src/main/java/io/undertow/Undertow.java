package io.undertow;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.undertow.ajp.AjpOpenListener;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.GSSAPIServerSubjectFactory;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.security.impl.FormAuthenticationMechanism;
import io.undertow.security.impl.GSSAPIAuthenticationMechanism;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpOpenListener;
import io.undertow.server.handlers.CookieHandler;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.cache.CacheHandler;
import io.undertow.server.handlers.cache.CachedHttpRequest;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.error.SimpleErrorPageHandler;
import io.undertow.server.handlers.form.FormEncodedDataHandler;
import io.undertow.websockets.api.WebSocketSessionHandler;
import io.undertow.websockets.core.handler.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.impl.WebSocketSessionConnectionCallback;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;

/**
 * Convenience class used to build an Undertow server.
 * <p/>
 * TODO: This API is still a work in progress
 *
 * @author Stuart Douglas
 */
public class Undertow {

    private final int bufferSize;
    private final int buffersPerRegion;
    private final int ioThreads;
    private final int workerThreads;
    private final int cacheSize;
    private final boolean directBuffers;
    private final List<ListenerConfig> listeners = new ArrayList<ListenerConfig>();
    private final List<VirtualHost> hosts = new ArrayList<VirtualHost>();

    private XnioWorker worker;
    private List<AcceptingChannel<? extends StreamConnection>> channels;
    private Xnio xnio;

    private Undertow(Builder builder) {
        this.bufferSize = builder.bufferSize;
        this.buffersPerRegion = builder.buffersPerRegion;
        this.ioThreads = builder.ioThreads;
        this.workerThreads = builder.workerThreads;
        this.cacheSize = builder.cacheSize;
        this.directBuffers = builder.directBuffers;
        this.listeners.addAll(builder.listeners);
        this.hosts.addAll(builder.hosts);
    }

    /**
     * @return A builder that can be used to create an Undertow server instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new Virtual Host, that can then be added to the server configuration.
     *
     * @param name The host name of the virtual host
     * @return The virtual host.
     * @see Builder#addVirtualHost(String)
     */
    public static VirtualHost virtualHost(final String name) {
        return new VirtualHost(false).addHostName(name);
    }

    /**
     * Creates a new security configuration, that can then be added to the server configuration.
     *
     * @param identityManager the identity manager to use.
     * @return The security config.
     */
    public static LoginConfig loginConfig(final IdentityManager identityManager) {
        return new LoginConfig(identityManager);
    }

    public synchronized void start() {
        xnio = Xnio.getInstance("nio", Undertow.class.getClassLoader());
        channels = new ArrayList<>();
        try {
            worker = xnio.createWorker(OptionMap.builder()
                    .set(Options.WORKER_WRITE_THREADS, ioThreads)
                    .set(Options.WORKER_READ_THREADS, ioThreads)
                    .set(Options.CONNECTION_HIGH_WATER, 1000000)
                    .set(Options.CONNECTION_LOW_WATER, 1000000)
                    .set(Options.WORKER_TASK_CORE_THREADS, workerThreads)
                    .set(Options.WORKER_TASK_MAX_THREADS, workerThreads)
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.CORK, true)
                    .getMap());

            OptionMap serverOptions = OptionMap.builder()
                    .set(Options.WORKER_ACCEPT_THREADS, ioThreads)
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.REUSE_ADDRESSES, true)
                    .getMap();

            Pool<ByteBuffer> buffers = new ByteBufferSlicePool(directBuffers ? BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR : BufferAllocator.BYTE_BUFFER_ALLOCATOR, bufferSize, bufferSize * buffersPerRegion);

            HttpHandler rootHandler = buildHandlerChain();

            for (ListenerConfig listener : listeners) {
                if (listener.type == ListenerType.AJP) {
                    AjpOpenListener openListener = new AjpOpenListener(buffers, bufferSize);
                    openListener.setRootHandler(rootHandler);
                    ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(openListener);
                    AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(listener.host), listener.port), acceptListener, serverOptions);
                    server.resumeAccepts();
                    channels.add(server);
                } else if (listener.type == ListenerType.HTTP) {
                    HttpOpenListener openListener = new HttpOpenListener(buffers, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), bufferSize);
                    openListener.setRootHandler(rootHandler);
                    ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(openListener);
                    AcceptingChannel<? extends StreamConnection> server = worker.createStreamConnectionServer(new InetSocketAddress(Inet4Address.getByName(listener.host), listener.port), acceptListener, serverOptions);
                    server.resumeAccepts();
                    channels.add(server);
                }
                //TODO: https
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void stop() {
        for (AcceptingChannel<? extends StreamConnection> channel : channels) {
            IoUtils.safeClose(channel);
        }
        channels = null;
        worker.shutdownNow();
        worker = null;
        xnio = null;
    }

    private HttpHandler buildHandlerChain() {
        final NameVirtualHostHandler virtualHostHandler = new NameVirtualHostHandler();
        for (VirtualHost host : hosts) {
            final PathHandler paths = new PathHandler();
            paths.setDefaultHandler(host.defaultHandler);
            for (final Map.Entry<String, HttpHandler> entry : host.handlers.entrySet()) {
                paths.addPath(entry.getKey(), entry.getValue());
            }
            HttpHandler handler = paths;
            for (HandlerWrapper wrapper : host.wrappers) {
                handler = wrapper.wrap(handler);
            }
            handler = addLoginConfig(handler, host.loginConfig);
            if (host.defaultHost) {
                virtualHostHandler.setDefaultHandler(handler);
            }
            for (String hostName : host.hostNames) {
                virtualHostHandler.addHost(hostName, handler);
            }

        }

        HttpHandler root = virtualHostHandler;
        root = new CookieHandler(root);
        root = new FormEncodedDataHandler(root);
        root = new SimpleErrorPageHandler(root);
        //TODO: multipart

        if (cacheSize > 0) {
            root = new CacheHandler(new DirectBufferCache<CachedHttpRequest>(1024, cacheSize * 1024 * 1024), root);
        }

        return root;
    }

    private static HttpHandler addLoginConfig(final HttpHandler toWrap, final LoginConfig config) {
        if (config == null) {
            return toWrap;
        }
        HttpHandler handler = toWrap;
        //TODO: we need a way of specifying fine grained login constrains
        handler = new AuthenticationCallHandler(handler);
        handler = new AuthenticationConstraintHandler(handler);
        final List<AuthenticationMechanism> mechanisms = new ArrayList<AuthenticationMechanism>();
        if (config.basic) {
            mechanisms.add(new BasicAuthenticationMechanism(config.realmName));
        }
        if (config.kerberos) {
            mechanisms.add(new GSSAPIAuthenticationMechanism(config.subjectFactory));
        }
        if (config.form) {
            mechanisms.add(new FormAuthenticationMechanism("FORM", config.loginPage, config.errorPage));
        }
        handler = new AuthenticationMechanismsHandler(handler, mechanisms);
        handler = new SecurityInitialHandler(config.authenticationMode, config.identityManager, handler);
        return handler;
    }


    public static enum ListenerType {
        HTTP,
        HTTPS,
        AJP
    }

    private static class ListenerConfig {
        final ListenerType type;
        final int port;
        final String host;

        private ListenerConfig(final ListenerType type, final int port, final String host) {
            this.type = type;
            this.port = port;
            this.host = host;
        }
    }

    public interface Host<T> {

        T addPathHandler(final String path, final HttpHandler handler);

        T addWebSocketHandler(final String path, WebSocketSessionHandler handler);

        T setDefaultHandler(final HttpHandler handler);

        T addHandlerWrapper(final HandlerWrapper wrapper);

        T setLoginConfig(final LoginConfig loginConfig);

    }

    public static class VirtualHost implements Host<VirtualHost> {

        private final List<String> hostNames = new ArrayList<String>();
        private final Map<String, HttpHandler> handlers = new HashMap<String, HttpHandler>();
        private final List<HandlerWrapper> wrappers = new ArrayList<HandlerWrapper>();
        private final boolean defaultHost;
        private LoginConfig loginConfig;
        private HttpHandler defaultHandler = ResponseCodeHandler.HANDLE_404;

        VirtualHost(final boolean defaultHost) {
            this.defaultHost = defaultHost;
        }


        public VirtualHost addHostName(final String hostName) {
            hostNames.add(hostName);
            return this;
        }

        public VirtualHost addPathHandler(final String path, final HttpHandler handler) {
            handlers.put(path, handler);
            return this;
        }

        @Override
        public VirtualHost addWebSocketHandler(final String path, final WebSocketSessionHandler handler) {
            handlers.put(path, new WebSocketProtocolHandshakeHandler(new WebSocketSessionConnectionCallback(handler)));
            return this;
        }

        public VirtualHost setDefaultHandler(final HttpHandler handler) {
            this.defaultHandler = handler;
            return this;
        }

        public VirtualHost addHandlerWrapper(final HandlerWrapper wrapper) {
            wrappers.add(wrapper);
            return this;
        }

        @Override
        public VirtualHost setLoginConfig(final LoginConfig loginConfig) {
            this.loginConfig = loginConfig;
            return this;
        }
    }

    public static class LoginConfig {
        private final IdentityManager identityManager;
        private boolean basic;
        private boolean digest;
        private boolean kerberos;
        private boolean form;
        private String realmName;
        private String errorPage, loginPage;
        private GSSAPIServerSubjectFactory subjectFactory;
        private AuthenticationMode authenticationMode = AuthenticationMode.PRO_ACTIVE;

        public LoginConfig(final IdentityManager identityManager) {
            this.identityManager = identityManager;
        }

        public LoginConfig basicAuth(final String realmName) {
            if (digest) {
                throw UndertowMessages.MESSAGES.authTypeCannotBeCombined("basic", "digest");
            } else if (form) {
                throw UndertowMessages.MESSAGES.authTypeCannotBeCombined("basic", "form");
            }
            basic = true;
            this.realmName = realmName;
            return this;
        }

        public LoginConfig digestAuth(final String realmName) {
            if (basic) {
                throw UndertowMessages.MESSAGES.authTypeCannotBeCombined("digest", "basic");
            } else if (form) {
                throw UndertowMessages.MESSAGES.authTypeCannotBeCombined("digest", "form");
            }
            digest = true;
            this.realmName = realmName;
            return this;
        }

        public LoginConfig kerberosAuth(GSSAPIServerSubjectFactory subjectFactory) {
            kerberos = true;
            this.subjectFactory = subjectFactory;
            return this;
        }

        public LoginConfig formAuth(final String loginPage, final String errorPage) {
            if (digest) {
                throw UndertowMessages.MESSAGES.authTypeCannotBeCombined("form", "digest");
            } else if (basic) {
                throw UndertowMessages.MESSAGES.authTypeCannotBeCombined("form", "basic");
            }
            this.loginPage = loginPage;
            this.errorPage = errorPage;
            form = true;
            return this;
        }

        public LoginConfig setAuthenticationMode(final AuthenticationMode authenticationMode) {
            this.authenticationMode = authenticationMode;
            return this;
        }
    }

    public static final class Builder implements Host<Builder> {

        private int bufferSize;
        private int buffersPerRegion;
        private int ioThreads;
        private int workerThreads;
        private boolean directBuffers;
        private int cacheSize;
        private final List<ListenerConfig> listeners = new ArrayList<ListenerConfig>();
        private final List<VirtualHost> hosts = new ArrayList<VirtualHost>();
        private final VirtualHost defaultHost = new VirtualHost(true);

        private Builder() {
            ioThreads = Runtime.getRuntime().availableProcessors();
            workerThreads = ioThreads * 8;
            long maxMemory = Runtime.getRuntime().maxMemory();
            //smaller than 64mb of ram we use 512b buffers
            if (maxMemory < 64 * 1024 * 1024) {
                //use 512b buffers
                directBuffers = false;
                bufferSize = 512;
                buffersPerRegion = 10;
            } else if (maxMemory < 128 * 1024 * 1024) {
                //use 1k buffers
                directBuffers = true;
                bufferSize = 1024;
                buffersPerRegion = 10;
            } else {
                //use 4k buffers
                directBuffers = true;
                bufferSize = 1024 * 4;
                buffersPerRegion = 20;
            }
            hosts.add(defaultHost);

        }

        public Undertow build() {
            return new Undertow(this);
        }

        /**
         * Enables caching for files, and other cachable responses.
         *
         * @param cacheSize The size of the cache, in megabytes.
         */
        public Builder enableCache(final int cacheSize) {
            this.cacheSize = cacheSize;
            return this;
        }

        public Builder addListener(int port, String host) {
            listeners.add(new ListenerConfig(ListenerType.HTTP, port, host));
            return this;
        }

        public Builder setBufferSize(final int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public Builder setBuffersPerRegion(final int buffersPerRegion) {
            this.buffersPerRegion = buffersPerRegion;
            return this;
        }

        public Builder setIoThreads(final int ioThreads) {
            this.ioThreads = ioThreads;
            return this;
        }

        public Builder setWorkerThreads(final int workerThreads) {
            this.workerThreads = workerThreads;
            return this;
        }

        public Builder setDirectBuffers(final boolean directBuffers) {
            this.directBuffers = directBuffers;
            return this;
        }

        public Builder addVirtualHost(final String hostName) {
            VirtualHost host = new VirtualHost(false);
            host.addHostName(hostName);
            hosts.add(host);
            return this;
        }

        @Override
        public Builder addPathHandler(final String path, final HttpHandler handler) {
            defaultHost.addPathHandler(path, handler);
            return this;
        }

        @Override
        public Builder addWebSocketHandler(final String path, final WebSocketSessionHandler handler) {
            defaultHost.addWebSocketHandler(path, handler);
            return this;
        }

        @Override
        public Builder setDefaultHandler(final HttpHandler handler) {
            defaultHost.setDefaultHandler(handler);
            return this;
        }

        @Override
        public Builder addHandlerWrapper(final HandlerWrapper wrapper) {
            defaultHost.addHandlerWrapper(wrapper);
            return this;
        }

        @Override
        public Builder setLoginConfig(final LoginConfig loginConfig) {
            defaultHost.setLoginConfig(loginConfig);
            return this;
        }

    }

}
