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
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.NetworkUtils;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.WebSocketCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import io.undertow.websockets.utils.FrameChecker;
import io.undertow.websockets.utils.WebSocketTestClient;
import org.junit.Test;
import org.xnio.FutureResult;
import org.xnio.Pooled;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket07ServerTest extends AbstractWebSocketServerTest {
    @Override
    protected WebSocketVersion getVersion() {
        return WebSocketVersion.V07;
    }

    @Test
    public void testPing() throws Exception {
        final AtomicBoolean connected = new AtomicBoolean(false);
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final WebSocketHttpExchange exchange, final WebSocketChannel channel) {
                connected.set(true);
                channel.getReceiveSetter().set(new AbstractReceiveListener() {
                    @Override
                    protected void onFullPingMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
                        final Pooled<ByteBuffer[]> data = message.getData();
                        WebSockets.sendPong(data.getResource(), channel, new WebSocketCallback<Void>() {
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
        final byte[] payload =  "payload".getBytes();

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + NetworkUtils.formatPossibleIpv6Address(DefaultServer.getHostAddress("default")) + ':' + DefaultServer.getHostPort("default") + '/'));
        client.connect();
        client.send(new PingWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(PongWebSocketFrame.class, payload, latch));
        latch.getIoFuture().get();
        client.destroy();
    }
}
