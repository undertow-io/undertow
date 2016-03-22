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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client which can be used to Test a websocket server
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class WebSocketTestClient {
    private final Bootstrap bootstrap = new Bootstrap();
    private Channel ch;
    private final URI uri;
    private final WebSocketVersion version;
    private volatile boolean closed;

    private static final AtomicInteger count = new AtomicInteger();

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
                WebSocketClientHandshakerFactory.newHandshaker(
                        uri, version, null, false, new DefaultHttpHeaders());

        EventLoopGroup group = new NioEventLoopGroup();
        final CountDownLatch handshakeLatch = new CountDownLatch(1);
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {

                        ChannelPipeline p = channel.pipeline();
                        p.addLast(
                                new HttpClientCodec(),
                                new HttpObjectAggregator(8192),
                                new WSClientHandler(handshaker, handshakeLatch));
                    }
                });

        // Connect
        ChannelFuture future =
                bootstrap.connect(
                        new InetSocketAddress(uri.getHost(), uri.getPort()));
        future.syncUninterruptibly();

        ch = future.channel();

        handshaker.handshake(ch).syncUninterruptibly();
        handshakeLatch.await();

        return this;
    }

    /**
     * Send the WebSocketFrame and call the FrameListener once a frame was received as response or
     * when an Exception was caught.
     */
    public WebSocketTestClient send(WebSocketFrame frame, final FrameListener listener) {
        ch.pipeline().addLast("responseHandler" + count.incrementAndGet(), new SimpleChannelInboundHandler<Object>() {

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof CloseWebSocketFrame) {
                    closed = true;
                }
                listener.onFrame((WebSocketFrame) msg);
                ctx.pipeline().remove(this);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                cause.printStackTrace();
                listener.onError(cause);
                ctx.pipeline().remove(this);
            }
        });
        ChannelFuture cf = ch.writeAndFlush(frame).syncUninterruptibly();
        if (!cf.isSuccess()) {
            listener.onError(cf.cause());
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
        //bootstrap.releaseExternalResources();
        if (ch != null) {
            ch.close().syncUninterruptibly();
        }
        try {
            bootstrap.group().shutdownGracefully(0, 1, TimeUnit.SECONDS).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
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

    private static final class WSClientHandler extends SimpleChannelInboundHandler<Object> {

        private final WebSocketClientHandshaker handshaker;
        private final CountDownLatch handshakeLatch;

        WSClientHandler(WebSocketClientHandshaker handshaker, CountDownLatch handshakeLatch) {
            super(false);
            this.handshaker = handshaker;
            this.handshakeLatch = handshakeLatch;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object o) throws Exception {

            Channel ch = ctx.channel();

            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ch, (FullHttpResponse) o);
                // the handshake response was processed upgrade is complete
                handshakeLatch.countDown();
                ReferenceCountUtil.release(o);
                return;
            }

            if (o instanceof FullHttpResponse) {
                FullHttpResponse response = (FullHttpResponse) o;
                ReferenceCountUtil.release(o);
                throw new Exception("Unexpected HttpResponse (status=" + response.getStatus() + ", content="
                        + response.content().toString(CharsetUtil.UTF_8) + ')');
            }
            ctx.fireChannelRead(o);
        }
    }
}


