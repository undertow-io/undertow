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
package io.undertow.websockets.core.protocol;

import io.netty.buffer.Unpooled;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.NetworkUtils;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.CloseMessage;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import io.undertow.websockets.utils.FrameChecker;
import io.undertow.websockets.utils.WebSocketTestClient;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.FutureResult;
import org.xnio.Pooled;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class AbstractWebSocketServerTest {

    @Test
    public void testText() throws Exception {
        if (getVersion() == WebSocketVersion.V00) {
            // ignore 00 tests for now
            return;
        }
        final AtomicBoolean connected = new AtomicBoolean(false);
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final WebSocketHttpExchange exchange, final WebSocketChannel channel) {
                connected.set(true);
                channel.getReceiveSetter().set(new AbstractReceiveListener() {
                    @Override
                    protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                        String string = message.getData();

                        if (string.equals("hello")) {
                            WebSockets.sendText("world", channel, null);
                        } else {
                            WebSockets.sendText(string, channel, null);
                        }
                    }
                });
                channel.resumeReceives();
            }
        }));

        final FutureResult<?> latch = new FutureResult();
        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + NetworkUtils.formatPossibleIpv6Address(DefaultServer.getHostAddress("default")) + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new TextWebSocketFrame(Unpooled.copiedBuffer("hello", CharsetUtil.US_ASCII)), new FrameChecker(TextWebSocketFrame.class, "world".getBytes(CharsetUtil.US_ASCII), latch));
        latch.getIoFuture().get();
        client.destroy();
    }

    @Test
    public void testBinary() throws Exception {
        if (getVersion() == WebSocketVersion.V00) {
            // ignore 00 tests for now
            return;
        }
        final AtomicBoolean connected = new AtomicBoolean(false);
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final WebSocketHttpExchange exchange, final WebSocketChannel channel) {
                connected.set(true);
                channel.getReceiveSetter().set(new AbstractReceiveListener() {

                    @Override
                    protected void onFullBinaryMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
                        final Pooled<ByteBuffer[]> data = message.getData();
                        WebSockets.sendBinary(data.getResource(), channel, new WebSocketCallback<Void>() {
                            @Override
                            public void complete(WebSocketChannel channel, Void context) {
                                data.close();
                            }

                            @Override
                            public void onError(WebSocketChannel channel, Void context, Throwable throwable) {
                                data.close();
                            }
                        });
                    }
                });
                channel.resumeReceives();
            }
        }));

        final FutureResult latch = new FutureResult();
        final byte[] payload = "payload".getBytes();

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + NetworkUtils.formatPossibleIpv6Address(DefaultServer.getHostAddress("default")) + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(BinaryWebSocketFrame.class, payload, latch));
        latch.getIoFuture().get();

        client.destroy();
    }

    @Test
    public void testCloseFrame() throws Exception {
        if (getVersion() == WebSocketVersion.V00) {
            // ignore 00 tests for now
            return;
        }
        final AtomicBoolean connected = new AtomicBoolean(false);
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final WebSocketHttpExchange exchange, final WebSocketChannel channel) {
                connected.set(true);
                channel.getReceiveSetter().set(new AbstractReceiveListener() {
                    @Override
                    protected void onFullCloseMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
                        message.getData().close();
                        channel.sendClose();
                    }
                });
                channel.resumeReceives();
            }
        }));

        final AtomicBoolean receivedResponse = new AtomicBoolean(false);

        final FutureResult latch = new FutureResult();
        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + NetworkUtils.formatPossibleIpv6Address(DefaultServer.getHostAddress("default")) + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new CloseWebSocketFrame(), new FrameChecker(CloseWebSocketFrame.class, new byte[0], latch));
        latch.getIoFuture().get();
        Assert.assertFalse(receivedResponse.get());
        client.destroy();
    }

    @Test
    public void testCloseOnPeerGone() throws Exception {
        if (getVersion() == WebSocketVersion.V00) {
            // ignore 00 tests for now
            return;
        }
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<CloseMessage> latch = new FutureResult();
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final WebSocketHttpExchange exchange, final WebSocketChannel channel) {
                connected.set(true);
                channel.getReceiveSetter().set(new AbstractReceiveListener() {

                    @Override
                    protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                        Assert.fail();
                    }

                    @Override
                    protected void onCloseMessage(CloseMessage msg, WebSocketChannel channel) {
                        latch.setResult(msg);
                    }

                    @Override
                    protected void onError(WebSocketChannel channel, Throwable t) {
                        Assert.fail();
                    }
                });
                channel.resumeReceives();
            }
        }));

        WebSocketTestClient client = new WebSocketTestClient(getVersion(),
                new URI("ws://" + NetworkUtils.formatPossibleIpv6Address(DefaultServer.getHostAddress("default")) + ":"
                        + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.destroy(true);
        latch.getIoFuture().await(5000, TimeUnit.MILLISECONDS);
        final CloseMessage msg = latch.getIoFuture().get();
        Assert.assertNotNull(msg);
        Assert.assertEquals(WebSocketMessages.MESSAGES.messageCloseWebSocket(), msg.getReason());
    }

    protected WebSocketVersion getVersion() {
        return WebSocketVersion.V00;
    }
}
