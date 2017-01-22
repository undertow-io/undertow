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

package io.undertow.server.handlers.proxy;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.ServerConnection;
import io.undertow.util.AttachmentKey;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.Channel;
import java.util.concurrent.TimeUnit;

/**
 * Simple proxy client provider. This provider simply proxies to another server, using a a one to one
 * connection strategy.
 *
 * {@link LoadBalancingProxyClient} should be used instead. This proxy client is too simplistic for
 * real world use cases, and it not set up to use SSL.
 *
 * @author Stuart Douglas
 */
@Deprecated
public class SimpleProxyClientProvider implements ProxyClient {

    private final URI uri;
    private final AttachmentKey<ClientConnection> clientAttachmentKey = AttachmentKey.create(ClientConnection.class);
    private final UndertowClient client;

    private static final ProxyTarget TARGET = new ProxyTarget() {};

    public SimpleProxyClientProvider(URI uri) {
        this.uri = uri;
        client = UndertowClient.getInstance();
    }

    @Override
    public ProxyTarget findTarget(HttpServerExchange exchange) {
        return TARGET;
    }

    @Override
    public void getConnection(ProxyTarget target, HttpServerExchange exchange, ProxyCallback<ProxyConnection> callback, long timeout, TimeUnit timeUnit) {
        ClientConnection existing = exchange.getConnection().getAttachment(clientAttachmentKey);
        if (existing != null) {
            if (existing.isOpen()) {
                //this connection already has a client, re-use it
                callback.completed(exchange, new ProxyConnection(existing, uri.getPath() == null ? "/" : uri.getPath()));
                return;
            } else {
                exchange.getConnection().removeAttachment(clientAttachmentKey);
            }
        }
        client.connect(new ConnectNotifier(callback, exchange), uri, exchange.getIoThread(), exchange.getConnection().getByteBufferPool(), OptionMap.EMPTY);
    }

    private final class ConnectNotifier implements ClientCallback<ClientConnection> {
        private final ProxyCallback<ProxyConnection> callback;
        private final HttpServerExchange exchange;

        private ConnectNotifier(ProxyCallback<ProxyConnection> callback, HttpServerExchange exchange) {
            this.callback = callback;
            this.exchange = exchange;
        }

        @Override
        public void completed(final ClientConnection connection) {
            final ServerConnection serverConnection = exchange.getConnection();
            //we attach to the connection so it can be re-used
            serverConnection.putAttachment(clientAttachmentKey, connection);
            serverConnection.addCloseListener(new ServerConnection.CloseListener() {
                @Override
                public void closed(ServerConnection serverConnection) {
                    IoUtils.safeClose(connection);
                }
            });
            connection.getCloseSetter().set(new ChannelListener<Channel>() {
                @Override
                public void handleEvent(Channel channel) {
                    serverConnection.removeAttachment(clientAttachmentKey);
                }
            });
            callback.completed(exchange, new ProxyConnection(connection, uri.getPath() == null ? "/" : uri.getPath()));
        }

        @Override
        public void failed(IOException e) {
            callback.failed(exchange);
        }
    }


}
