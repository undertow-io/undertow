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

package io.undertow.examples.websockets_extension;

import io.undertow.Undertow;
import io.undertow.examples.UndertowExample;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.extensions.PerMessageDeflateHandshake;
import io.undertow.websockets.spi.WebSocketHttpExchange;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.resource;

/**
 * @author Stuart Douglas
 */
@UndertowExample("Web Socket Extensions")
public class WebSocketServer {

    public static void main(final String[] args) {
        // Demonstrates how to use Websocket Protocol Handshake to enable Per-message deflate
        Undertow server = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setHandler(path()
                        .addPrefixPath("/myapp",
                            new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {

                              @Override
                              public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
                                channel.getReceiveSetter().set(new AbstractReceiveListener() {

                                  @Override
                                  protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                                    WebSockets.sendText(message.getData(), channel, null);
                                  }
                                });
                                channel.resumeReceives();
                              }
                            }).addExtension(new PerMessageDeflateHandshake(false, 6)))
                        .addPrefixPath("/", resource(new ClassPathResourceManager(WebSocketServer.class.getClassLoader(), WebSocketServer.class.getPackage())).addWelcomeFiles("index.html")))
                .build();
        server.start();
    }

}
