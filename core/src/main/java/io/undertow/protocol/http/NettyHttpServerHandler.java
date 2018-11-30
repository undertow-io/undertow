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

import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;

public class NettyHttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private final ExecutorService blockingExecutor;
    private final HttpHandler rootHandler;


    private HttpServerConnection connection;

    public NettyHttpServerHandler(ExecutorService blockingExecutor, HttpHandler rootHandler) {
        this.blockingExecutor = blockingExecutor;
        this.rootHandler = rootHandler;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            if(connection == null) {
                connection = new HttpServerConnection(ctx, blockingExecutor);
            }
            HttpServerExchange exchange = new HttpServerExchange(connection);
            connection.setExchange(exchange);
            for(Map.Entry<String, String> header : request.headers()) {
                exchange.getRequestHeaders().put(new HttpString(header.getKey()), header.getValue());
            }
            exchange.setRelativePath(request.uri());
            exchange.setRequestPath(request.uri());
            exchange.setRequestURI(request.uri());
            exchange.setProtocol(Protocols.HTTP_1_1);
            exchange.setQueryString("");
            if(msg instanceof HttpContent) {
                connection.queueContent((HttpContent)msg);
            }
            if(msg instanceof LastHttpContent) {
                Connectors.terminateRequest(exchange);
            }
            Connectors.executeRootHandler(rootHandler, exchange);
        } else if(msg instanceof HttpContent) {

        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
