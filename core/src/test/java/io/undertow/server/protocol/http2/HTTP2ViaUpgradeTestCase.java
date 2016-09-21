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

package io.undertow.server.protocol.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2FrameReader;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2InboundFrameLogger;
import io.netty.handler.codec.http2.Http2OutboundFrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LogLevel;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.Options;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Tests the load balancing proxy
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class HTTP2ViaUpgradeTestCase {

    static Undertow server;

    static volatile String message;

    private static final LinkedBlockingDeque<String> messages = new LinkedBlockingDeque<>();

    @BeforeClass
    public static void setup() throws URISyntaxException {
        final SessionCookieConfig sessionConfig = new SessionCookieConfig();
        int port = DefaultServer.getHostPort("default");
        server = Undertow.builder()
                .addHttpListener(port + 1, DefaultServer.getHostAddress("default"))
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(Handlers.header(new Http2UpgradeHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        if (!(exchange.getConnection() instanceof Http2ServerConnection)) {
                            throw new RuntimeException("Not HTTP2");
                        }
                        exchange.getResponseHeaders().add(new HttpString("X-Custom-Header"), "foo");
                        exchange.getResponseSender().send(message);
                    }
                }, "h2c", "h2c-17"), Headers.SEC_WEB_SOCKET_ACCEPT_STRING, "fake")) //work around Netty bug, it assumes that every upgrade request that does not have this header is an old style websocket upgrade
                .build();

        server.start();
    }

    @AfterClass
    public static void stop() {
        server.stop();
    }

    @Test
    public void testHttp2WithNettyClient() throws Exception {

        message = "Hello World";

        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Http2ClientInitializer initializer = new Http2ClientInitializer(Integer.MAX_VALUE);

        try {
            // Configure the client.
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            final int port = DefaultServer.getHostPort("default") + 1;
            final String host = DefaultServer.getHostAddress("default");
            b.remoteAddress(host, port);
            b.handler(initializer);

            // Start the client.

            Channel channel = b.connect().syncUninterruptibly().channel();

            Http2SettingsHandler http2SettingsHandler = initializer.settingsHandler();
            http2SettingsHandler.awaitSettings(5, TimeUnit.SECONDS);
            HttpResponseHandler responseHandler = initializer.responseHandler();
            int streamId = 3;
            URI hostName = URI.create("http://" + host + ':' + port);
            System.err.println("Sending request(s)...");
            // Create a simple GET request.
            final ChannelPromise promise = channel.newPromise();
            responseHandler.put(streamId, promise);
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, hostName.toString());
            request.headers().add(HttpHeaderNames.HOST, hostName);
            request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
            request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);
            channel.writeAndFlush(request);
            streamId += 2;
            promise.await(10, TimeUnit.SECONDS);
            Assert.assertEquals(message, messages.poll());
            System.out.println("Finished HTTP/2 request(s)");

            // Wait until the connection is closed.
            channel.close().syncUninterruptibly();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }


    static class Http2ClientInitializer extends ChannelInitializer<SocketChannel> {
        private static final Http2FrameLogger logger = new Http2FrameLogger(LogLevel.INFO, Http2ClientInitializer.class);

        private final int maxContentLength;
        private HttpToHttp2ConnectionHandler connectionHandler;
        private HttpResponseHandler responseHandler;
        private Http2SettingsHandler settingsHandler;

        Http2ClientInitializer(int maxContentLength) {
            this.maxContentLength = maxContentLength;
        }

        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            final Http2Connection connection = new DefaultHttp2Connection(false);
            connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
                    .connection(connection)
                    .frameListener(new DelegatingDecompressorFrameListener(connection,
                            new InboundHttp2ToHttpAdapterBuilder(connection)
                                    .maxContentLength(maxContentLength)
                                    .propagateSettings(true)
                                    .build()))

                    .build();
            responseHandler = new HttpResponseHandler();
            settingsHandler = new Http2SettingsHandler(ch.newPromise());
            configureClearText(ch);
        }

        public HttpResponseHandler responseHandler() {
            return responseHandler;
        }

        public Http2SettingsHandler settingsHandler() {
            return settingsHandler;
        }

        protected void configureEndOfPipeline(ChannelPipeline pipeline) {
            pipeline.addLast(settingsHandler, responseHandler);
        }
        /**
         * Configure the pipeline for a cleartext upgrade from HTTP to HTTP/2.
         */
        private void configureClearText(SocketChannel ch) {
            HttpClientCodec sourceCodec = new HttpClientCodec();
            Http2ClientUpgradeCodec upgradeCodec = new Http2ClientUpgradeCodec(connectionHandler);
            HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536);

            ch.pipeline().addLast(sourceCodec,
                    upgradeHandler,
                    new UpgradeRequestHandler(),
                    new UserEventLogger());
        }

        /**
         * A handler that triggers the cleartext upgrade to HTTP/2 by sending an initial HTTP request.
         */
        private final class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                DefaultFullHttpRequest upgradeRequest =
                        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/sdf");
                upgradeRequest.headers().add(Headers.HOST_STRING, "default");
                ctx.writeAndFlush(upgradeRequest);

                ctx.fireChannelActive();

                // Done with this handler, remove it from the pipeline.
                ctx.pipeline().remove(this);

                configureEndOfPipeline(ctx.pipeline());
            }
        }

        /**
         * Class that logs any User Events triggered on this channel.
         */
        private static class UserEventLogger extends ChannelInboundHandlerAdapter {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                System.out.println("User Event Triggered: " + evt);
                ctx.fireUserEventTriggered(evt);
            }
        }

        private static Http2FrameReader frameReader() {
            return new Http2InboundFrameLogger(new DefaultHttp2FrameReader(), logger);
        }

        private static Http2FrameWriter frameWriter() {
            return new Http2OutboundFrameLogger(new DefaultHttp2FrameWriter(), logger);
        }
    }

    static class Http2SettingsHandler extends SimpleChannelInboundHandler<Http2Settings> {
        private ChannelPromise promise;

        /**
         * Create new instance
         *
         * @param promise Promise object used to notify when first settings are received
         */
        Http2SettingsHandler(ChannelPromise promise) {
            this.promise = promise;
        }

        /**
         * Wait for this handler to be added after the upgrade to HTTP/2, and for initial preface
         * handshake to complete.
         *
         * @param timeout Time to wait
         * @param unit {@link java.util.concurrent.TimeUnit} for {@code timeout}
         * @throws Exception if timeout or other failure occurs
         */
        public void awaitSettings(long timeout, TimeUnit unit) throws Exception {
            if (!promise.awaitUninterruptibly(timeout, unit)) {
                throw new IllegalStateException("Timed out waiting for settings");
            }
            if (!promise.isSuccess()) {
                throw new RuntimeException(promise.cause());
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2Settings msg) throws Exception {
            promise.setSuccess();

            // Only care about the first settings message
            ctx.pipeline().remove(this);
        }
    }

    static class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

        private SortedMap<Integer, ChannelPromise> streamidPromiseMap;

        HttpResponseHandler() {
            streamidPromiseMap = new TreeMap<Integer, ChannelPromise>();
        }

        /**
         * Create an association between an anticipated response stream id and a {@link io.netty.channel.ChannelPromise}
         *
         * @param streamId The stream for which a response is expected
         * @param promise The promise object that will be used to wait/notify events
         * @return The previous object associated with {@code streamId}
         * @see HttpResponseHandler#awaitResponses(long, java.util.concurrent.TimeUnit)
         */
        public ChannelPromise put(int streamId, ChannelPromise promise) {
            return streamidPromiseMap.put(streamId, promise);
        }

        /**
         * Wait (sequentially) for a time duration for each anticipated response
         *
         * @param timeout Value of time to wait for each response
         * @param unit Units associated with {@code timeout}
         * @see HttpResponseHandler#put(int, io.netty.channel.ChannelPromise)
         */
        public void awaitResponses(long timeout, TimeUnit unit) {
            Iterator<Entry<Integer, ChannelPromise>> itr = streamidPromiseMap.entrySet().iterator();
            while (itr.hasNext()) {
                Entry<Integer, ChannelPromise> entry = itr.next();
                ChannelPromise promise = entry.getValue();
                if (!promise.awaitUninterruptibly(timeout, unit)) {
                    throw new IllegalStateException("Timed out waiting for response on stream id " + entry.getKey());
                }
                if (!promise.isSuccess()) {
                    throw new RuntimeException(promise.cause());
                }
                System.out.println("---Stream id: " + entry.getKey() + " received---");
                itr.remove();
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
            Integer streamId = msg.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
            if (streamId == null) {
                System.err.println("HttpResponseHandler unexpected message received: " + msg);
                return;
            }

            ChannelPromise promise = streamidPromiseMap.get(streamId);
            if (promise == null) {
                System.err.println("Message received for unknown stream id " + streamId);
            } else {
                // Do stuff with the message (for now just print it)
                ByteBuf content = msg.content();
                if (content.isReadable()) {
                    int contentLength = content.readableBytes();
                    byte[] arr = new byte[contentLength];
                    content.readBytes(arr);
                    messages.add(new String(arr, StandardCharsets.UTF_8));
                }

                promise.setSuccess();
            }
        }
    }
}
