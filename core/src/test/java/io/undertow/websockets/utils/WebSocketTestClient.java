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
package io.undertow.websockets.utils;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.jboss.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Client which can be used to Test a websocket server
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class WebSocketTestClient {
    private final ClientBootstrap bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory());
    private Channel ch;
    private final URI uri;
    private final WebSocketVersion version;
    private volatile boolean closed;

    public WebSocketTestClient(WebSocketVersion version, URI uri) {
        this.uri = uri;
        this.version = version;
    }

    /**
     * Connect the WebSocket client
     *
     * @throws Exception
     */
    public WebSocketTestClient connect() throws Exception {
        String protocol = uri.getScheme();
        if (!"ws".equals(protocol)) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        final WebSocketClientHandshaker handshaker =
                new WebSocketClientHandshakerFactory().newHandshaker(
                        uri, version, null, false, Collections.<String, String>emptyMap());

        final CountDownLatch handshakeLatch = new CountDownLatch(1);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();

                pipeline.addLast("decoder", new HttpResponseDecoder());
                pipeline.addLast("encoder", new HttpRequestEncoder());
                pipeline.addLast("ws-handler", new WSClientHandler(handshaker, handshakeLatch));
                return pipeline;
            }
        });

        // Connect
        ChannelFuture future =
                bootstrap.connect(
                        new InetSocketAddress(uri.getHost(), uri.getPort()));
        future.syncUninterruptibly();

        ch = future.getChannel();

        handshaker.handshake(ch).syncUninterruptibly();
        handshakeLatch.await();

        return this;
    }

    /**
     * Send the WebSocketFrame and call the FrameListener once a frame was received as response or
     * when an Exception was caught.
     */
    public WebSocketTestClient send(WebSocketFrame frame, final FrameListener listener) {
        ch.getPipeline().addLast("responseHandler", new SimpleChannelUpstreamHandler() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
                if (e.getMessage() instanceof CloseWebSocketFrame) {
                    closed = true;
                }
                listener.onFrame((WebSocketFrame) e.getMessage());
                ctx.getPipeline().remove(this);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
                listener.onError(e.getCause());
                ctx.getPipeline().remove(this);
            }
        });
        ChannelFuture cf = ch.write(frame).syncUninterruptibly();
        if (!cf.isSuccess()) {
            listener.onError(cf.getCause());
        }
        return this;
    }

    /**
     * Destroy the client and also close open connections if any exist
     */
    public void destroy() {
        if (!closed) {
            final CountDownLatch latch = new CountDownLatch(1);
            send(new CloseWebSocketFrame(), new FrameListener() {
                @Override
                public void onFrame(WebSocketFrame frame) {
                    latch.countDown();
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }
            });
            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        bootstrap.releaseExternalResources();
        if (ch != null) {
            ch.close().syncUninterruptibly();
        }
    }

    public interface FrameListener {
        /**
         * Is called if an WebSocketFrame was received
         */
        void onFrame(WebSocketFrame frame);

        /**
         * Is called if an error occurred
         */
        void onError(Throwable t);
    }

    private static final class WSClientHandler extends SimpleChannelUpstreamHandler {

        private final WebSocketClientHandshaker handshaker;
        private final CountDownLatch handshakeLatch;

        public WSClientHandler(WebSocketClientHandshaker handshaker, CountDownLatch handshakeLatch) {
            this.handshaker = handshaker;
            this.handshakeLatch = handshakeLatch;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            Channel ch = ctx.getChannel();

            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ch, (HttpResponse) e.getMessage());
                // the handshake response was processed upgrade is complete
                handshakeLatch.countDown();
                return;
            }

            if (e.getMessage() instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) e.getMessage();
                throw new Exception("Unexpected HttpResponse (status=" + response.getStatus() + ", content="
                        + response.getContent().toString(CharsetUtil.UTF_8) + ')');
            }
            // foward to the next handler
            super.messageReceived(ctx, e);
        }


    }
}


