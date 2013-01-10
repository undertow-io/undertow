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
package io.undertow.websockets.protocol.version07;

import io.undertow.server.HttpServerExchange;
import io.undertow.test.utils.DefaultServer;
import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.handler.WebSocketConnectionCallback;
import io.undertow.websockets.handler.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.protocol.version00.WebSocket00ServerTest;
import io.undertow.websockets.utils.WebSocketTestClient;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.junit.Assert;
import org.junit.Test;
import org.xnio.ChannelListener;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket07ServerTest extends WebSocket00ServerTest {
    @Override
    protected WebSocketVersion getVersion() {
        return WebSocketVersion.V07;
    }

    @Test
    public void testPing() throws Exception {
        if (getVersion() == WebSocketVersion.V07) {
            // Skip till Netty 3.6.2.Final was released
            return;
        }
        final AtomicBoolean connected = new AtomicBoolean(false);
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {
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
                            Assert.assertEquals(WebSocketFrameType.PING, ws.getType());
                            ByteBuffer buf = ByteBuffer.allocate(32);
                            while (ws.read(buf) != -1) {
                                // consume
                            }
                            buf.flip();

                            StreamSinkFrameChannel sink = channel.send(WebSocketFrameType.PONG, buf.remaining());
                            Assert.assertEquals(WebSocketFrameType.PONG, sink.getType());
                            while (buf.hasRemaining()) {
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

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ':' + DefaultServer.getHostPort("default") + '/'));
        client.connect();
        client.send(new PingWebSocketFrame(ChannelBuffers.wrappedBuffer(payload)), new FrameChecker(PongWebSocketFrame.class, payload, latch));
        latch.await();
        client.destroy();
    }

    @Override
    public void testText() throws Exception {
        if (getVersion() == WebSocketVersion.V07) {
            // Skip till Netty 3.6.2.Final was released
            return;
        }
        super.testText();
    }

    @Override
    public void testBinary() throws Exception {
        if (getVersion() == WebSocketVersion.V07) {
            // Skip till Netty 3.6.2.Final was released
            return;
        }
        super.testBinary();
    }

    @Override
    public void testCloseFrame() throws Exception {
        if (getVersion() == WebSocketVersion.V07) {
            // Skip till Netty 3.6.2.Final was released
            return;
        }
        super.testCloseFrame();
    }
}
