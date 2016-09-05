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

import io.undertow.UndertowMessages;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.UndertowClient;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.core.WebSocketVersion;

import io.undertow.websockets.extensions.ExtensionHandshake;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.http.HttpUpgrade;
import org.xnio.http.RedirectException;
import org.xnio.ssl.XnioSsl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
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

    private static final int MAX_REDIRECTS = Integer.getInteger("io.undertow.websockets.max-redirects", 5);

    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, final ByteBufferPool bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version) {
        return connect(worker, bufferPool, optionMap, uri, version, null);
    }

    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, XnioSsl ssl, final ByteBufferPool bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version) {
        return connect(worker, ssl, bufferPool, optionMap, uri, version, null);
    }

    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, final ByteBufferPool bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version, WebSocketClientNegotiation clientNegotiation) {
        return connect(worker, null, bufferPool, optionMap, uri, version, clientNegotiation);
    }

    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, XnioSsl ssl, final ByteBufferPool bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version, WebSocketClientNegotiation clientNegotiation) {
        return connect(worker, ssl, bufferPool, optionMap, uri, version, clientNegotiation, null);
    }

    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, XnioSsl ssl, final ByteBufferPool bufferPool, final OptionMap optionMap, final URI uri, WebSocketVersion version, WebSocketClientNegotiation clientNegotiation, Set<ExtensionHandshake> clientExtensions) {
        return connect(worker, ssl, bufferPool, optionMap, null, uri, version, clientNegotiation, clientExtensions);
    }

    @Deprecated
    public static IoFuture<WebSocketChannel> connect(XnioWorker worker, XnioSsl ssl, final ByteBufferPool bufferPool, final OptionMap optionMap, InetSocketAddress bindAddress, final URI uri, WebSocketVersion version, WebSocketClientNegotiation clientNegotiation, Set<ExtensionHandshake> clientExtensions) {
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
        private final ByteBufferPool bufferPool;
        private final URI uri;

        private XnioSsl ssl;
        private OptionMap optionMap = OptionMap.EMPTY;
        private InetSocketAddress bindAddress;
        private WebSocketVersion version = WebSocketVersion.V13;
        private WebSocketClientNegotiation clientNegotiation;
        private Set<ExtensionHandshake> clientExtensions;
        private URI proxyUri;
        private XnioSsl proxySsl;

        public ConnectionBuilder(XnioWorker worker, ByteBufferPool bufferPool, URI uri) {
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

        public ByteBufferPool getBufferPool() {
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

        public URI getProxyUri() {
            return proxyUri;
        }

        public ConnectionBuilder setProxyUri(URI proxyUri) {
            this.proxyUri = proxyUri;
            return this;
        }

        public XnioSsl getProxySsl() {
            return proxySsl;
        }

        public ConnectionBuilder setProxySsl(XnioSsl proxySsl) {
            this.proxySsl = proxySsl;
            return this;
        }

        public IoFuture<WebSocketChannel> connect() {
            return connectImpl(uri, new FutureResult<WebSocketChannel>(), 0);
        }
        private IoFuture<WebSocketChannel> connectImpl(final URI uri, final FutureResult<WebSocketChannel> ioFuture, final int redirectCount) {
            WebSocketLogger.REQUEST_LOGGER.debugf("Opening websocket connection to %s", uri);
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
            originalHeaders.put(Headers.HOST_STRING, uri.getHost() + ":" + newUri.getPort());
            final Map<String, List<String>> headers = new HashMap<>();
            for(Map.Entry<String, String> entry : originalHeaders.entrySet()) {
                List<String> list = new ArrayList<>();
                list.add(entry.getValue());
                headers.put(entry.getKey(), list);
            }
            if (clientNegotiation != null) {
                clientNegotiation.beforeRequest(headers);
            }
            InetSocketAddress toBind = bindAddress;
            String sysBind = System.getProperty(BIND_PROPERTY);
            if(toBind == null && sysBind != null) {
                toBind = new InetSocketAddress(sysBind, 0);
            }
            if(proxyUri != null) {
               UndertowClient.getInstance().connect(new ClientCallback<ClientConnection>() {
                    @Override
                    public void completed(final ClientConnection connection) {
                        int port = uri.getPort() > 0 ? uri.getPort() : uri.getScheme().equals("https") || uri.getScheme().equals("wss") ? 443 : 80;
                        ClientRequest cr = new ClientRequest()
                                .setMethod(Methods.CONNECT)
                                .setPath(uri.getHost() + ":" + port)
                                .setProtocol(Protocols.HTTP_1_1);
                        cr.getRequestHeaders().put(Headers.HOST, proxyUri.getHost() + ":" + (proxyUri.getPort() > 0 ? proxyUri.getPort() : 80));
                        connection.sendRequest(cr, new ClientCallback<ClientExchange>() {
                            @Override
                            public void completed(ClientExchange result) {
                                result.setResponseListener(new ClientCallback<ClientExchange>() {
                                    @Override
                                    public void completed(ClientExchange response) {
                                        try {
                                            if (response.getResponse().getResponseCode() == 200) {
                                                try {
                                                    StreamConnection targetConnection = connection.performUpgrade();
                                                    WebSocketLogger.REQUEST_LOGGER.debugf("Established websocket connection to %s", uri);
                                                    if (uri.getScheme().equals("wss") || uri.getScheme().equals("https")) {
                                                        handleConnectionWithExistingConnection(((UndertowXnioSsl) ssl).wrapExistingConnection(targetConnection, optionMap));
                                                    } else {
                                                        handleConnectionWithExistingConnection(targetConnection);
                                                    }
                                                } catch (IOException e) {
                                                    ioFuture.setException(e);
                                                } catch (Exception e) {
                                                    ioFuture.setException(new IOException(e));
                                                }
                                            } else {
                                                ioFuture.setException(UndertowMessages.MESSAGES.proxyConnectionFailed(response.getResponse().getResponseCode()));
                                            }
                                        } catch (Exception e) {
                                            ioFuture.setException(new IOException(e));
                                        }
                                    }

                                    private void handleConnectionWithExistingConnection(StreamConnection targetConnection) {
                                        final IoFuture<?> result;

                                        result = HttpUpgrade.performUpgrade(targetConnection, newUri, headers, new WebsocketConnectionListener(optionMap, handshake, newUri, ioFuture), handshake.handshakeChecker(newUri, headers));

                                        result.addNotifier(new IoFuture.Notifier<Object, Object>() {
                                            @Override
                                            public void notify(IoFuture<?> res, Object attachment) {
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
                                    }

                                    @Override
                                    public void failed(IOException e) {
                                        ioFuture.setException(e);
                                    }
                                });
                            }
                            @Override
                            public void failed(IOException e) {
                                ioFuture.setException(e);
                            }
                        });
                    }
                    @Override
                    public void failed(IOException e) {
                        ioFuture.setException(e);
                    }
                }, bindAddress, proxyUri, worker, proxySsl,  bufferPool, optionMap);

            } else {
                final IoFuture<?> result;
                if (ssl != null) {
                    result = HttpUpgrade.performUpgrade(worker, ssl, toBind, newUri, headers, new WebsocketConnectionListener(optionMap, handshake, newUri, ioFuture), null, optionMap, handshake.handshakeChecker(newUri, headers));
                } else {
                    result = HttpUpgrade.performUpgrade(worker, toBind, newUri, headers, new WebsocketConnectionListener(optionMap, handshake, newUri, ioFuture), null, optionMap, handshake.handshakeChecker(newUri, headers));
                }
                result.addNotifier(new IoFuture.Notifier<Object, Object>() {
                    @Override
                    public void notify(IoFuture<?> res, Object attachment) {
                        if (res.getStatus() == IoFuture.Status.FAILED) {
                            IOException exception = res.getException();
                            if(exception instanceof RedirectException) {
                                if(redirectCount == MAX_REDIRECTS) {
                                    ioFuture.setException(UndertowMessages.MESSAGES.tooManyRedirects(exception));
                                } else {
                                    String path = ((RedirectException) exception).getLocation();
                                    try {
                                        connectImpl(new URI(path), ioFuture, redirectCount + 1);
                                    } catch (URISyntaxException e) {
                                        ioFuture.setException(new IOException(e));
                                    }
                                }
                            } else {
                                ioFuture.setException(exception);
                            }
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
            }
            return ioFuture.getIoFuture();
        }

        private class WebsocketConnectionListener implements ChannelListener<StreamConnection> {
            private final OptionMap options;
            private final WebSocketClientHandshake handshake;
            private final URI newUri;
            private final FutureResult<WebSocketChannel> ioFuture;

            WebsocketConnectionListener(OptionMap options, WebSocketClientHandshake handshake, URI newUri, FutureResult<WebSocketChannel> ioFuture) {
                this.options = options;
                this.handshake = handshake;
                this.newUri = newUri;
                this.ioFuture = ioFuture;
            }

            @Override
            public void handleEvent(StreamConnection channel) {
                WebSocketChannel result = handshake.createChannel(channel, newUri.toString(), bufferPool, options);
                ioFuture.setResult(result);
            }
        }
    }

    /**
     * Creates a new connection builder that can be used to create a web socket connection.
     * @param worker The XnioWorker to use for the connection
     * @param bufferPool The buffer pool
     * @param uri The connection URI
     * @return The connection builder
     */
    public static ConnectionBuilder connectionBuilder(XnioWorker worker, ByteBufferPool bufferPool, URI uri) {
        return new ConnectionBuilder(worker, bufferPool, uri);
    }


    private WebSocketClient() {

    }
}
