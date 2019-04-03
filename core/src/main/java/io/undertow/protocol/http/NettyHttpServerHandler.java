/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.protocol.http;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLEngine;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.undertow.server.ConnectionSSLSessionInfo;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;

public class NettyHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private final ExecutorService blockingExecutor;
    private final HttpHandler rootHandler;
    private final SSLEngine engine;


    private HttpServerConnection connection;

    public NettyHttpServerHandler(ExecutorService blockingExecutor, HttpHandler rootHandler, SSLEngine engine) {
        this.blockingExecutor = blockingExecutor;
        this.rootHandler = rootHandler;
        this.engine = engine;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            if (connection == null) {
                connection = new HttpServerConnection(ctx, blockingExecutor, engine == null ? null : new ConnectionSSLSessionInfo(engine.getSession()));
                ctx.channel().closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
                    @Override
                    public void operationComplete(Future<? super Void> future) throws Exception {
                        connection.closed(new ClosedChannelException());
                    }
                });
            }
            HttpServerExchange exchange = new HttpServerExchange(connection, request.headers());
            Connectors.setExchangeRequestPath(exchange, request.uri(), "UTF-8", true, false, new StringBuilder());
            exchange.setRequestMethod(new HttpString(request.method().name()));
            if (engine == null) {
                exchange.setRequestScheme("http");
            } else {
                exchange.setRequestScheme("https");
            }
            exchange.setProtocol(Protocols.HTTP_1_1);
            if (msg instanceof HttpContent) {
                connection.addData((HttpContent) msg);
            }
            if (msg instanceof LastHttpContent) {
                Connectors.terminateRequest(exchange);
            }

            connection.newExchange(exchange, rootHandler);
        } else if (msg instanceof HttpContent) {
            connection.addData((HttpContent) msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        connection.closed(new IOException(cause));
        ctx.close();
    }
}
