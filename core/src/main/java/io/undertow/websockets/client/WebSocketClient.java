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

package io.undertow.websockets.client;

import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;

import io.undertow.websockets.extensions.ExtensionHandshake;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.http.HttpUpgrade;
import org.xnio.ssl.XnioSsl;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Web socket client.
 *
 * @author Stuart Douglas
 */
public class WebSocketClient {

    public static final String BIND_PROPERTY = "io.undertow.websockets.BIND_ADDRESS";


    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version) {
        return connect(worker, bufferPool, optionMap, uri, version, null);
    }

    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version) {
        return connect(worker, ssl, bufferPool, optionMap, uri, version, null);
    }

    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version, WebSocketClientNegotiation clientNegotiation) {
        return connect(worker, null, bufferPool, optionMap, uri, version, clientNegotiation);
    }

    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version, WebSocketClientNegotiation clientNegotiation) {
        return connect(worker, ssl, bufferPool, optionMap, uri, version, clientNegotiation, null);
    }

    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version, WebSocketClientNegotiation clientNegotiation, Set<ExtensionHandshake> clientExtensions) {
        return connect(worker, ssl, bufferPool, optionMap, null, uri, version, clientNegotiation, clientExtensions);
    }

    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, XnioSsl ssl, final Pool<ByteBuffer> bufferPool, final OptionMap optionMap, InetSocketAddress bindAddress, final URI uri, WebSocketVersion version, WebSocketClientNegotiation clientNegotiation, Set<ExtensionHandshake> clientExtensions) {
        return connectionBuilder(worker, bufferPool, uri)
                .setSsl(ssl)
                .setOptionMap(optionMap)
                .setBindAddress(bindAddress)
                .setVersion(version)
                .setClientNegotiation(clientNegotiation)
                .setClientExtensions(clientExtensions)
                .connect();
    }

    public static class ConnectionBuilder {
        private final XnioWorker worker;
        private final Pool<ByteBuffer> bufferPool;
        private final URI uri;

        private XnioSsl ssl;
        private OptionMap optionMap = OptionMap.EMPTY;
        private InetSocketAddress bindAddress;
        private WebSocketVersion version = WebSocketVersion.V13;
        private WebSocketClientNegotiation clientNegotiation;
        private Set<ExtensionHandshake> clientExtensions;

        public ConnectionBuilder(XnioWorker worker, Pool<ByteBuffer> bufferPool, URI uri) {
            this.worker = worker;
            this.bufferPool = bufferPool;
            this.uri = uri;
        }

        public XnioWorker getWorker() {
            return worker;
        }

        public URI getUri() {
            return uri;
        }

        public XnioSsl getSsl() {
            return ssl;
        }

        public ConnectionBuilder setSsl(XnioSsl ssl) {
            this.ssl = ssl;
            return this;
        }

        public Pool<ByteBuffer> getBufferPool() {
            return bufferPool;
        }

        public OptionMap getOptionMap() {
            return optionMap;
        }

        public ConnectionBuilder setOptionMap(OptionMap optionMap) {
            this.optionMap = optionMap;
            return this;
        }

        public InetSocketAddress getBindAddress() {
            return bindAddress;
        }

        public ConnectionBuilder setBindAddress(InetSocketAddress bindAddress) {
            this.bindAddress = bindAddress;
            return this;
        }

        public WebSocketVersion getVersion() {
            return version;
        }

        public ConnectionBuilder setVersion(WebSocketVersion version) {
            this.version = version;
            return this;
        }

        public WebSocketClientNegotiation getClientNegotiation() {
            return clientNegotiation;
        }

        public ConnectionBuilder setClientNegotiation(WebSocketClientNegotiation clientNegotiation) {
            this.clientNegotiation = clientNegotiation;
            return this;
        }

        public Set<ExtensionHandshake> getClientExtensions() {
            return clientExtensions;
        }

        public ConnectionBuilder setClientExtensions(Set<ExtensionHandshake> clientExtensions) {
            this.clientExtensions = clientExtensions;
            return this;
        }

        public IoFuture<WebSocketChannel> connect() {
            final FutureResult<WebSocketChannel> ioFuture = new FutureResult<>();
            final String scheme = uri.getScheme().equals("wss") ? "https" : "http";
            final URI newUri;
            try {
                newUri = new URI(scheme, uri.getUserInfo(), uri.getHost(), uri.getPort() == -1 ? (uri.getScheme().equals("wss") ? 443 : 80) : uri.getPort(), uri.getPath().isEmpty() ? "/" : uri.getPath(), uri.getQuery(), uri.getFragment());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            final WebSocketClientHandshake handshake = WebSocketClientHandshake.create(version, newUri, clientNegotiation, clientExtensions);
            final Map<String, String> originalHeaders = handshake.createHeaders();
            originalHeaders.put(Headers.ORIGIN_STRING, scheme + "://" + uri.getHost());
            final Map<String, List<String>> headers = new HashMap<>();
            for(Map.Entry<String, String> entry : originalHeaders.entrySet()) {
                List<String> list = new ArrayList<>();
                list.add(entry.getValue());
                headers.put(entry.getKey(), list);
            }
            if (clientNegotiation != null) {
                clientNegotiation.beforeRequest(headers);
            }
            final IoFuture<? extends StreamConnection> result;
            InetSocketAddress toBind = bindAddress;
            String sysBind = System.getProperty(BIND_PROPERTY);
            if(toBind == null && sysBind != null) {
                toBind = new InetSocketAddress(sysBind, 0);
            }
            if (ssl != null) {
                result = HttpUpgrade.performUpgrade(worker, ssl, toBind, newUri, headers, new ChannelListener<StreamConnection>() {
                    @Override
                    public void handleEvent(StreamConnection channel) {
                        WebSocketChannel result = handshake.createChannel(channel, newUri.toString(), bufferPool);
                        ioFuture.setResult(result);
                    }
                }, null, optionMap, handshake.handshakeChecker(newUri, headers));
            } else {
                result = HttpUpgrade.performUpgrade(worker, toBind, newUri, headers, new ChannelListener<StreamConnection>() {
                    @Override
                    public void handleEvent(StreamConnection channel) {
                        WebSocketChannel result = handshake.createChannel(channel, newUri.toString(), bufferPool);
                        ioFuture.setResult(result);
                    }
                }, null, optionMap, handshake.handshakeChecker(newUri, headers));
            }
            result.addNotifier(new IoFuture.Notifier<StreamConnection, Object>() {
                @Override
                public void notify(IoFuture<? extends StreamConnection> res, Object attachment) {
                    if (res.getStatus() == IoFuture.Status.FAILED) {
                        ioFuture.setException(res.getException());
                    }
                }
            }, null);
            ioFuture.addCancelHandler(new Cancellable() {
                @Override
                public Cancellable cancel() {
                    result.cancel();
                    return null;
                }
            });
            return ioFuture.getIoFuture();
        }

    }

    /**
     * Creates a new connection builder that can be used to create a web socket connection.
     * @param worker The XnioWorker to use for the connection
     * @param bufferPool The buffer pool
     * @param uri The connection URI
     * @return The connection builder
     */
    public static ConnectionBuilder connectionBuilder(XnioWorker worker, Pool<ByteBuffer> bufferPool, URI uri) {
        return new ConnectionBuilder(worker, bufferPool, uri);
    }


    private WebSocketClient() {

    }
}
