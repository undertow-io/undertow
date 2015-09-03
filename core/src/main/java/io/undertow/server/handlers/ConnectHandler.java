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

package io.undertow.server.handlers;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.util.Methods;
import io.undertow.util.SameThreadExecutor;
import io.undertow.util.StatusCodes;
import io.undertow.util.Transfer;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSinkChannel;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;

/**
 *
 * Handlers HTTP CONNECT requests, allowing the server to act as a forward proxy.
 *
 * WARNING: Do not enable this without some kind of authentication / IP based restriction scheme
 * in place, as this will allow malicious actors to use your server as an open relay.
 *
 * @author Stuart Douglas
 */
public class ConnectHandler implements HttpHandler {

    private final HttpHandler next;
    private final Predicate allowed;

    public ConnectHandler(HttpHandler next) {
        this(next, Predicates.truePredicate());
    }

    public ConnectHandler(HttpHandler next, Predicate allowed) {
        this.next = next;
        this.allowed = allowed;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if(exchange.getRequestMethod().equals(Methods.CONNECT)) {
            if(!allowed.resolve(exchange)) {
                exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED);//not sure if this is the best response
                return;
            }
            String[] parts = exchange.getRequestPath().split(":");
            if(parts.length != 2) {
                exchange.setStatusCode(StatusCodes.BAD_REQUEST);//not sure if this is the best response
                return;
            }
            final String host = parts[0];
            final Integer port = Integer.parseInt(parts[1]);
            exchange.dispatch(SameThreadExecutor.INSTANCE, new Runnable() {
                @Override
                public void run() {
                    exchange.getConnection().getIoThread().openStreamConnection(new InetSocketAddress(host, port), new ChannelListener<StreamConnection>() {
                        @Override
                        public void handleEvent(final StreamConnection clientChannel) {
                            exchange.acceptConnectRequest(new HttpUpgradeListener() {
                                @Override
                                public void handleUpgrade(StreamConnection streamConnection, HttpServerExchange exchange) {
                                    final ClosingExceptionHandler handler = new ClosingExceptionHandler(streamConnection, clientChannel);
                                    Transfer.initiateTransfer(clientChannel.getSourceChannel(), streamConnection.getSinkChannel(), ChannelListeners.closingChannelListener(), ChannelListeners.writeShutdownChannelListener(ChannelListeners.<StreamSinkChannel>flushingChannelListener(ChannelListeners.closingChannelListener(), ChannelListeners.closingChannelExceptionHandler()), ChannelListeners.closingChannelExceptionHandler()), handler, handler, exchange.getConnection().getByteBufferPool());
                                    Transfer.initiateTransfer(streamConnection.getSourceChannel(), clientChannel.getSinkChannel(), ChannelListeners.closingChannelListener(), ChannelListeners.writeShutdownChannelListener(ChannelListeners.<StreamSinkChannel>flushingChannelListener(ChannelListeners.closingChannelListener(), ChannelListeners.closingChannelExceptionHandler()), ChannelListeners.closingChannelExceptionHandler()), handler, handler, exchange.getConnection().getByteBufferPool());
                                }
                            });
                            exchange.setStatusCode(200);
                            exchange.endExchange();
                        }
                    }, OptionMap.create(Options.TCP_NODELAY, true)).addNotifier(new IoFuture.Notifier<StreamConnection, Object>() {
                        @Override
                        public void notify(IoFuture<? extends StreamConnection> ioFuture, Object attachment) {
                            if(ioFuture.getStatus() == IoFuture.Status.FAILED) {
                                exchange.setStatusCode(503);
                                exchange.endExchange();
                            }
                        }
                    },null);
                }
            });
        } else {
            next.handleRequest(exchange);
        }
    }


    private static final class ClosingExceptionHandler implements ChannelExceptionHandler<Channel> {

        private final Closeable[] toClose;

        private ClosingExceptionHandler(Closeable... toClose) {
            this.toClose = toClose;
        }


        @Override
        public void handleException(Channel channel, IOException exception) {
            IoUtils.safeClose(channel);
            IoUtils.safeClose(toClose);
        }
    }
}
