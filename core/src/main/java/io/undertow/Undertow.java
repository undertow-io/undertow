package io.undertow;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.undertow.ajp.AjpOpenListener;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.GSSAPIServerSubjectFactory;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpOpenListener;
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
import org.xnio.channels.SslConnection;
import org.xnio.ssl.XnioSsl;

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
    private final boolean directBuffers;
    private final List<ListenerConfig> listeners = new ArrayList<ListenerConfig>();
    private final HttpHandler rootHandler;

    private XnioWorker worker;
    private List<AcceptingChannel<? extends StreamConnection>> channels;
    private Xnio xnio;

    private Undertow(Builder builder) {
        this.bufferSize = builder.bufferSize;
        this.buffersPerRegion = builder.buffersPerRegion;
        this.ioThreads = builder.ioThreads;
        this.workerThreads = builder.workerThreads;
        this.directBuffers = builder.directBuffers;
        this.listeners.addAll(builder.listeners);
        this.rootHandler = builder.handler;
    }

    /**
     * @return A builder that can be used to create an Undertow server instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public synchronized void start() {
        xnio = Xnio.getInstance("nio", Undertow.class.getClassLoader());
        channels = new ArrayList<AcceptingChannel<? extends StreamConnection>>();
        try {
            worker = xnio.createWorker(OptionMap.builder()
                    .set(Options.WORKER_IO_THREADS, ioThreads)
                    .set(Options.CONNECTION_HIGH_WATER, 1000000)
                    .set(Options.CONNECTION_LOW_WATER, 1000000)
                    .set(Options.WORKER_TASK_CORE_THREADS, workerThreads)
                    .set(Options.WORKER_TASK_MAX_THREADS, workerThreads)
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.CORK, true)
                    .getMap());

            OptionMap serverOptions = OptionMap.builder()
                    .set(Options.WORKER_IO_THREADS, ioThreads)
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.REUSE_ADDRESSES, true)
                    .getMap();

            Pool<ByteBuffer> buffers = new ByteBufferSlicePool(directBuffers ? BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR : BufferAllocator.BYTE_BUFFER_ALLOCATOR, bufferSize, bufferSize * buffersPerRegion);

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
                } else if (listener.type == ListenerType.HTTPS){
                    HttpOpenListener openListener = new HttpOpenListener(buffers, OptionMap.create(UndertowOptions.BUFFER_PIPELINED_DATA, true), bufferSize);
                    openListener.setRootHandler(rootHandler);
                    ChannelListener<AcceptingChannel<StreamConnection>> acceptListener = ChannelListeners.openListenerAdapter(openListener);
                    XnioSsl xnioSsl = xnio.getSslProvider(OptionMap.create(Options.USE_DIRECT_BUFFERS, true));
                    AcceptingChannel < SslConnection > sslServer = xnioSsl.createSslConnectionServer(worker, new InetSocketAddress(Inet4Address.getByName(listener.host), listener.port), (ChannelListener) acceptListener, serverOptions);
                    sslServer.resumeAccepts();
                    channels.add(sslServer);
                }

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

    public static final class Builder {

        private int bufferSize;
        private int buffersPerRegion;
        private int ioThreads;
        private int workerThreads;
        private boolean directBuffers;
        private final List<ListenerConfig> listeners = new ArrayList<ListenerConfig>();
        private HttpHandler handler;

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

        }

        public Undertow build() {
            return new Undertow(this);
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

        public Builder setHandler(final HttpHandler handler) {
            this.handler = handler;
            return this;
        }
    }

}
