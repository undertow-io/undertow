/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets.protocol.version00;

import io.undertow.server.HttpServerExchange;
import io.undertow.test.utils.DefaultServer;
import io.undertow.util.StringReadChannelListener;
import io.undertow.util.StringWriteChannelListener;
import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.handler.WebSocketConnectionCallback;
import io.undertow.websockets.handler.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.utils.WebSocketTestClient;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.xnio.ChannelListener;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@RunWith(DefaultServer.class)
public class WebSocket00ServerTest {

    @org.junit.Test
    public void testText() throws Exception {
        final AtomicBoolean connected = new AtomicBoolean(false);
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler("", new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final HttpServerExchange exchange, final WebSocketChannel channel) {
                connected.set(true);
                channel.getReceiveSetter().set(new ChannelListener<WebSocketChannel>() {
                    @Override
                    public void handleEvent(final WebSocketChannel channel) {
                        try {
                            final StreamSourceFrameChannel ws = channel.receive();
                            if (ws == null) {
                                return;
                            }
                            new StringReadChannelListener(exchange.getConnection().getBufferPool()) {
                                @Override
                                protected void stringDone(final String string) {
                                    if (string.equals("hello")) {
                                        new StringWriteChannelListener("world")
                                                .setup(channel.send(WebSocketFrameType.TEXT, "world".length()));
                                    } else {
                                        new StringWriteChannelListener(string)
                                                .setup(channel.send(WebSocketFrameType.TEXT, string.length()));
                                    }
                                }

                                @Override
                                protected void error(final IOException e) {
                                    e.printStackTrace();
                                    new StringWriteChannelListener("ERROR")
                                            .setup(channel.send(WebSocketFrameType.TEXT, "ERROR".length()));
                                }
                            }.setup(ws);
                            channel.getReceiveSetter().set(null);

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                channel.resumeReceives();
            }
        }));

        final AtomicReference<String> result = new AtomicReference<String>();
        final CountDownLatch latch = new CountDownLatch(1);
        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new TextWebSocketFrame(ChannelBuffers.copiedBuffer("hello", CharsetUtil.US_ASCII)), new FrameChecker(TextWebSocketFrame.class, "world".getBytes(CharsetUtil.US_ASCII), latch));
        latch.await();
        client.destroy();
    }

    @org.junit.Test
    public void testBinary() throws Exception {
        final AtomicBoolean connected = new AtomicBoolean(false);
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler("/", new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final HttpServerExchange exchange, final WebSocketChannel channel) {
                connected.set(true);
                channel.getReceiveSetter().set(new ChannelListener<WebSocketChannel>() {
                    @Override
                    public void handleEvent(final WebSocketChannel channel) {
                        try {
                            final StreamSourceFrameChannel ws = channel.receive();
                            if (ws == null) {
                                return;
                            }
                            Assert.assertEquals(WebSocketFrameType.BINARY, ws.getType());
                            ByteBuffer buf = ByteBuffer.allocate(32);
                            while (ws.read(buf) != -1);
                            buf.flip();

                            StreamSinkFrameChannel sink = channel.send(WebSocketFrameType.BINARY, buf.remaining());
                            Assert.assertEquals(WebSocketFrameType.BINARY, sink.getType());
                            while(buf.hasRemaining()) {
                                sink.write(buf);
                            }
                            Assert.assertTrue(sink.flush());
                            channel.getReceiveSetter().set(null);

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                channel.resumeReceives();
            }
        }));

        final CountDownLatch latch = new CountDownLatch(1);
        final byte[] payload =  "payload".getBytes();

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new BinaryWebSocketFrame(ChannelBuffers.wrappedBuffer(payload)), new FrameChecker(BinaryWebSocketFrame.class, payload, latch));
        latch.await();

        client.destroy();
    }

    @org.junit.Test
    public void testCloseFrame() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final AtomicBoolean connected = new AtomicBoolean(false);
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler("/", new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final HttpServerExchange exchange, final WebSocketChannel channel) {
                connected.set(true);
                channel.getReceiveSetter().set(new ChannelListener<WebSocketChannel>() {
                    @Override
                    public void handleEvent(final WebSocketChannel channel) {
                        try {
                            final StreamSourceFrameChannel ws = channel.receive();
                            if (ws == null) {
                                return;
                            }
                            Assert.assertEquals(WebSocketFrameType.CLOSE, ws.getType());
                            channel.close();
                            channel.getReceiveSetter().set(null);
                            latch.countDown();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                channel.resumeReceives();
            }
        }));

        final AtomicBoolean receivedResponse = new AtomicBoolean(false);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new CloseWebSocketFrame(), new WebSocketTestClient.FrameListener() {
            @Override
            public void onFrame(WebSocketFrame frame) {
                receivedResponse.set(true);
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }
        });
        latch.await();
        Assert.assertFalse(receivedResponse.get());
        client.destroy();
    }

    protected WebSocketVersion getVersion() {
        return WebSocketVersion.V00;
    }

    public final class FrameChecker implements WebSocketTestClient.FrameListener {
        private final Class<? extends WebSocketFrame> clazz;
        private final byte[] expectedPayload;
        private final CountDownLatch latch;

        public FrameChecker(Class<? extends WebSocketFrame> clazz, byte[] expectedPayload, CountDownLatch latch) {
            this.clazz = clazz;
            this.expectedPayload = expectedPayload;
            this.latch = latch;
        }


        @Override
        public void onFrame(WebSocketFrame frame) {
            try {
                Assert.assertTrue(clazz.isInstance(frame));

                ChannelBuffer buf = frame.getBinaryData();
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);

                Assert.assertArrayEquals(expectedPayload, data);
            } finally {
                latch.countDown();
            }
        }

        @Override
        public void onError(Throwable t) {
            try {
                t.printStackTrace();
                Assert.fail();
            } finally {
                latch.countDown();
            }
        }
    }
}
