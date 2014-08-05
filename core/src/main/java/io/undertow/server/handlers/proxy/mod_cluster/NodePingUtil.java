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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.ProxyCallback;
import io.undertow.server.handlers.proxy.ProxyConnection;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.SameThreadExecutor;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.ssl.XnioSsl;

/**
 * Utilities to ping a remote node.
 *
 * @author Emanuel Muckenhuber
 */
// TODO this needs timeouts
class NodePingUtil {

    interface PingCallback {

        /**
         * Ping completed.
         */
        void completed();

        /**
         * Ping failed.
         */
        void failed();

    }

    private static final ClientRequest PING_REQUEST;

    static {
        final ClientRequest request = new ClientRequest();
        request.setMethod(Methods.OPTIONS);
        request.setPath("*");
        request.getRequestHeaders().add(Headers.USER_AGENT, "mod_cluster ping");
        PING_REQUEST = request;
    }

    /**
     * Try to open a socket connection to given address.
     *
     * @param address     the socket address
     * @param exchange    the http servers exchange
     * @param callback    the ping callback
     * @param options     the options
     */
    static void pingHost(InetSocketAddress address, HttpServerExchange exchange, PingCallback callback, OptionMap options) {

        final XnioIoThread thread = exchange.getIoThread();
        final XnioWorker worker = thread.getWorker();
        final Runnable r = new HostPingTask(address, worker, callback, options);
        exchange.dispatch(exchange.isInIoThread() ? SameThreadExecutor.INSTANCE : thread, r);
    }

    /**
     * Try to ping a server using the undertow client.
     *
     * @param connection    the connection URI
     * @param callback      the ping callback
     * @param exchange      the http servers exchange
     * @param client        the undertow client
     * @param xnioSsl       the ssl setup
     * @param options       the options
     */
    static void pingHttpClient(URI connection, PingCallback callback, HttpServerExchange exchange, UndertowClient client, XnioSsl xnioSsl, OptionMap options) {

        final XnioIoThread thread = exchange.getIoThread();
        final Runnable r = new HttpClientPingTask(connection, callback, thread, client, xnioSsl, exchange.getConnection().getBufferPool(), options);
        exchange.dispatch(exchange.isInIoThread() ? SameThreadExecutor.INSTANCE : thread, r);
    }

    /**
     * Try to ping a node using it's connection pool.
     *
     * @param node        the node
     * @param exchange    the http servers exchange
     * @param callback    the ping callback
     */
    static void pingNode(final Node node, final HttpServerExchange exchange, final PingCallback callback) {
        if (node == null) {
            callback.failed();
            return;
        }

        final int timeout = node.getNodeConfig().getTimeout();
        exchange.dispatch(exchange.isInIoThread() ? SameThreadExecutor.INSTANCE : exchange.getIoThread(), new Runnable() {
            @Override
            public void run() {
                node.getConnectionPool().connect(null, exchange, new ProxyCallback<ProxyConnection>() {
                    @Override
                    public void completed(final HttpServerExchange exchange, ProxyConnection result) {
                        exchange.dispatch(SameThreadExecutor.INSTANCE, new ConnectionPoolPingTask(result, callback));
                    }

                    @Override
                    public void failed(HttpServerExchange exchange) {
                        callback.failed();
                    }

                }, timeout, TimeUnit.SECONDS, false);
            }
        });
    }

    static class ConnectionPoolPingTask implements Runnable {

        private final PingCallback callback;
        private final ProxyConnection proxyConnection;

        ConnectionPoolPingTask(ProxyConnection proxyConnection, PingCallback callback) {
            this.proxyConnection = proxyConnection;
            this.callback = callback;
        }

        @Override
        public void run() {

            // TODO AJP has a special ping thing
            proxyConnection.getConnection().sendRequest(PING_REQUEST, new ClientCallback<ClientExchange>() {
                @Override
                public void completed(final ClientExchange result) {
                    result.setResponseListener(new ClientCallback<ClientExchange>() {
                        @Override
                        public void completed(ClientExchange result) {
                            final RequestExchangeListener listener = new RequestExchangeListener(callback, result, false);
                            result.setResponseListener(listener);
                        }

                        @Override
                        public void failed(IOException e) {
                            callback.failed();
                            IoUtils.safeClose(result.getConnection());
                        }
                    });
                }

                @Override
                public void failed(IOException e) {
                    callback.failed();
                }
            });
        }
    }

    static class HostPingTask implements Runnable {

        private final InetSocketAddress address;
        private final PingCallback callback;
        private final XnioWorker worker;
        private final OptionMap options;

        HostPingTask(InetSocketAddress address, XnioWorker worker, PingCallback callback, OptionMap options) {
            this.address = address;
            this.worker = worker;
            this.callback = callback;
            this.options = options;
        }

        @Override
        public void run() {
            try {
                final IoFuture<StreamConnection> future = worker.openStreamConnection(address, new ChannelListener<StreamConnection>() {
                    @Override
                    public void handleEvent(StreamConnection channel) {
                        IoUtils.safeClose(channel); // Close the channel right away
                    }
                }, options);

                future.addNotifier(new IoFuture.HandlingNotifier<StreamConnection, Void>() {

                    @Override
                    public void handleCancelled(Void attachment) {
                        callback.failed();
                    }

                    @Override
                    public void handleFailed(IOException exception, Void attachment) {
                        callback.failed();
                    }

                    @Override
                    public void handleDone(StreamConnection data, Void attachment) {
                        callback.completed();
                    }
                }, null);

            } catch (Exception e) {
                callback.failed();
            }
        }
    }

    static class HttpClientPingTask implements Runnable {

        private URI connection;
        private PingCallback callback;
        private XnioIoThread thread;
        private UndertowClient client;
        private XnioSsl xnioSsl;
        private Pool<ByteBuffer> bufferPool;
        private OptionMap options;

        HttpClientPingTask(URI connection, PingCallback callback, XnioIoThread thread, UndertowClient client, XnioSsl xnioSsl, Pool<ByteBuffer> bufferPool, OptionMap options) {
            this.connection = connection;
            this.callback = callback;
            this.thread = thread;
            this.client = client;
            this.xnioSsl = xnioSsl;
            this.bufferPool = bufferPool;
            this.options = options;
        }

        @Override
        public void run() {

            // TODO AJP has a special ping thing
            client.connect(new ClientCallback<ClientConnection>() {
                @Override
                public void completed(final ClientConnection result) {
                    result.sendRequest(PING_REQUEST, new ClientCallback<ClientExchange>() {
                        @Override
                        public void completed(ClientExchange result) {
                            final RequestExchangeListener listener = new RequestExchangeListener(callback, result, true);
                            result.setResponseListener(listener);
                        }

                        @Override
                        public void failed(IOException e) {
                            callback.failed();
                            IoUtils.safeClose(result);
                        }
                    });
                }

                @Override
                public void failed(IOException e) {
                    callback.failed();
                }
            }, connection, thread, xnioSsl, bufferPool, options);

        }
    }

    static class RequestExchangeListener implements ClientCallback<ClientExchange> {

        private final PingCallback callback;
        private final ClientExchange exchange;
        private final boolean closeConnection;

        RequestExchangeListener(PingCallback callback, ClientExchange exchange, boolean closeConnection) {
            this.callback = callback;
            this.exchange = exchange;
            this.closeConnection = closeConnection;
        }

        @Override
        public void completed(final ClientExchange result) {
            final ChannelListener<StreamSourceChannel> listener = ChannelListeners.drainListener(Long.MAX_VALUE, new ChannelListener<StreamSourceChannel>() {
                @Override
                public void handleEvent(StreamSourceChannel channel) {
                    final int responseCode = result.getResponse().getResponseCode();
                    // TODO this should actually check the HTTP 200 OK
                    callback.completed();
                    if (closeConnection) {
                        IoUtils.safeClose(exchange.getConnection());
                    }
                }
            }, new ChannelExceptionHandler<StreamSourceChannel>() {
                @Override
                public void handleException(StreamSourceChannel channel, IOException exception) {
                    callback.failed();
                    IoUtils.safeClose(exchange.getConnection());
                }
            });
            StreamSourceChannel responseChannel = result.getResponseChannel();
            responseChannel.getReadSetter().set(listener);
            responseChannel.resumeReads();
            listener.handleEvent(responseChannel);
        }

        @Override
        public void failed(IOException e) {
            callback.failed();
            IoUtils.safeClose(exchange.getConnection());
        }
    }

}
