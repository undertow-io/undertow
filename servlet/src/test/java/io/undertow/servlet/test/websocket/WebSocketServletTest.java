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

package io.undertow.servlet.test.websocket;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import io.undertow.servlet.websockets.WebSocketServlet;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.NetworkUtils;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import io.undertow.websockets.utils.FrameChecker;
import io.undertow.websockets.utils.WebSocketTestClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.FutureResult;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.Servlet;

/**
 * @author Stuart Douglas
 */
@HttpOneOnly
@RunWith(DefaultServer.class)
public class WebSocketServletTest {
    public static final Charset US_ASCII = StandardCharsets.US_ASCII;

    @Test
    public void testText() throws Exception {


        final AtomicBoolean connected = new AtomicBoolean(false);

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentUtils.setupServlet(new ServletInfo("websocket", WebSocketServlet.class,
                new ImmediateInstanceFactory<Servlet>(new WebSocketServlet(new WebSocketConnectionCallback() {
                    @Override
                    public void onConnect(final WebSocketHttpExchange exchange, final WebSocketChannel channel) {
                        connected.set(true);
                        channel.getReceiveSetter().set(new AbstractReceiveListener() {

                            @Override
                            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                                final String string = message.getData();
                                if(string.equals("hello")) {
                                    WebSockets.sendText("world", channel, null);
                                } else {
                                    WebSockets.sendText(string, channel, null);
                                }
                            }
                        });
                        channel.resumeReceives();
                    }
                })))
                .addMapping("/*"));

        final FutureResult latch = new FutureResult();
        WebSocketTestClient client = new WebSocketTestClient(io.netty.handler.codec.http.websocketx.WebSocketVersion.V13, new URI("ws://" + NetworkUtils.formatPossibleIpv6Address(DefaultServer.getHostAddress("default")) + ":" + DefaultServer.getHostPort("default") + "/servletContext/"));
        client.connect();
        client.send(new TextWebSocketFrame(Unpooled.copiedBuffer("hello", US_ASCII)), new FrameChecker(TextWebSocketFrame.class, "world".getBytes(US_ASCII), latch));
        latch.getIoFuture().get();
        client.destroy();
    }
}
