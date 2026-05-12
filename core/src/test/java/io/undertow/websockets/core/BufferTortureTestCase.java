/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.core;

import java.io.IOException;

import org.xnio.OptionMap;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.Headers;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.extensions.PerMessageDeflateHandshake;
import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * Testcases for various torture scenarios.
 *
 * @author Avishek Sarkar
 * @author baranowb
 *
 */
public class BufferTortureTestCase extends BufferTortureTestBase {

    @DefaultServer.BeforeServerStarts
    public static void beforeTests() {
        WebSocketProtocolHandshakeHandler wsHandler = Handlers.websocket(new WebSocketConnectionCallback() {
            @Override
            public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
                upgradesAccepted.incrementAndGet();

                channel.getReceiveSetter().set(new AbstractReceiveListener() {
                    @Override
                    protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                        messagesReceived.incrementAndGet();
                        lastMessage = message.getData();
                        messageLatch.countDown();
                    }

                    @Override
                    protected void onError(WebSocketChannel channel, Throwable error) {
                        error.printStackTrace();
                        super.onError(channel, error);
                    }

                    @Override
                    protected long getMaxBinaryBufferSize() {
                        return 100000;
                    }

                    @Override
                    protected long getMaxTextBufferSize() {
                        return 100000;
                    }

                });
                channel.resumeReceives();
            }
        });

        // Enable permessage-deflate with default config (no buffer limit)
        wsHandler.addExtension(new PerMessageDeflateHandshake(false, PerMessageDeflateHandshake.DEFAULT_DEFLATER, true, true,
                PerMessageDeflateHandshake.DEFAULT_MAX_BUFFER_SIZE * 3));

        // Origin checking wrapper
        HttpHandler originCheckHandler = exchange -> {
            // Check if this is a WebSocket upgrade
            String upgrade = exchange.getRequestHeaders().getFirst(Headers.UPGRADE);
            if ("websocket".equalsIgnoreCase(upgrade)) {
                originCheckCalled.set(true);
                String origin = exchange.getRequestHeaders().getFirst(Headers.ORIGIN);

                if (origin == null || !allowedOrigins.contains(origin)) {
                    upgradesRejected.incrementAndGet();
                    exchange.setStatusCode(403);
                    exchange.getResponseSender().send("Forbidden: Origin not allowed");
                    return;
                }
            }
            wsHandler.handleRequest(exchange);
        };

        DefaultServer.setRootHandler(originCheckHandler);
        // TODO: Add this once UNDERTOW-2760 is done
        // DefaultServer.setServerOptions(OptionMap.create(UndertowOptions.WEB_SOCKETS_SIZE_BINARY,
        // 20000L).create(UndertowOptions.WEB_SOCKETS_SIZE_TEXT, 20000L));

    }

    @DefaultServer.AfterServerStops
    public static void afterTest() {
        DefaultServer.setServerOptions(OptionMap.EMPTY);
    }

}
