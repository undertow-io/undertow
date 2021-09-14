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

package io.undertow.websockets.client.version13;

import java.io.IOException;
import java.net.URI;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.undertow.Undertow;
import io.undertow.protocols.ssl.UndertowXnioSsl;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ConnectHandler;
import io.undertow.testutils.ProxyIgnore;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.StringWriteChannelListener;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.protocol.server.AutobahnWebSocketServer;

import static io.undertow.testutils.StopServerWithExternalWorkerUtils.stopWorker;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class WebSocketClient13TestCase {
    private static XnioWorker worker;

    private static Undertow server;

    private static final Deque<String> connectLog = new LinkedBlockingDeque<>();

    @BeforeClass
    public static void setup() throws IOException {
        DefaultServer.setRootHandler(AutobahnWebSocketServer.getRootHandler());
        Xnio xnio = Xnio.getInstance(DefaultServer.class.getClassLoader());
        worker = xnio.createWorker(OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 2)
                .set(Options.CONNECTION_HIGH_WATER, 1000000)
                .set(Options.CONNECTION_LOW_WATER, 1000000)
                .set(Options.WORKER_TASK_CORE_THREADS, 30)
                .set(Options.WORKER_TASK_MAX_THREADS, 30)
                .set(Options.TCP_NODELAY, true)
                .set(Options.CORK, true)
                .getMap());

        final ConnectHandler handler = new ConnectHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.setStatusCode(500);
            }
        });

        DefaultServer.startSSLServer();

        server = Undertow.builder().addHttpListener(DefaultServer.getHostPort("default") + 10, DefaultServer.getHostAddress("default"))
                .setHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        connectLog.add(exchange.getRequestMethod() + " " + exchange.getRelativePath());
                        handler.handleRequest(exchange);
                    }
                })
                .build();
        server.start();
    }

    @AfterClass
    public static void stopAndShutdown() throws IOException {
        server.stop();
        server = null;
        DefaultServer.stopSSLServer();
        stopWorker(worker);
    }

    @Test
    public void testTextMessage() throws Exception {

        final WebSocketChannel webSocketChannel = WebSocketClient.connectionBuilder(worker, DefaultServer.getBufferPool(), new URI(DefaultServer.getDefaultServerURL())).connect().get();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        webSocketChannel.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                String data = message.getData();
                result.set(data);
                latch.countDown();
            }

            @Override
            protected void onError(WebSocketChannel channel, Throwable error) {
                super.onError(channel, error);
                error.printStackTrace();
                latch.countDown();
            }
        });
        webSocketChannel.resumeReceives();


        StreamSinkFrameChannel sendChannel = webSocketChannel.send(WebSocketFrameType.TEXT);
        new StringWriteChannelListener("Hello World").setup(sendChannel);

        latch.await(10, TimeUnit.SECONDS);
        Assert.assertEquals("Hello World", result.get());
        webSocketChannel.sendClose();
    }

    @Test
    public void testTextMessageWss() throws Exception {
        testTextMessageSecure("wss://");
    }

    @Test
    public void testTextMessageHttps() throws Exception {
        testTextMessageSecure("https://");
    }

    public void testTextMessageSecure(final String urlProtocol) throws Exception {

        UndertowXnioSsl ssl = new UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY, DefaultServer.getClientSSLContext());
        final WebSocketClient.ConnectionBuilder connectionBuilder = WebSocketClient.connectionBuilder(worker, DefaultServer.getBufferPool(), new URI(urlProtocol + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostSSLPort("default")))
                .setSsl(ssl);
        IoFuture<WebSocketChannel> future = connectionBuilder.connect();
        future.await(4, TimeUnit.SECONDS);
        final WebSocketChannel webSocketChannel = future.get();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        webSocketChannel.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                String data = message.getData();
                result.set(data);
                latch.countDown();
            }

            @Override
            protected void onError(WebSocketChannel channel, Throwable error) {
                super.onError(channel, error);
                error.printStackTrace();
                latch.countDown();
            }
        });
        webSocketChannel.resumeReceives();


        StreamSinkFrameChannel sendChannel = webSocketChannel.send(WebSocketFrameType.TEXT);
        new StringWriteChannelListener("Hello World").setup(sendChannel);

        latch.await(10, TimeUnit.SECONDS);
        Assert.assertEquals("Hello World", result.get());
        webSocketChannel.sendClose();
    }

    @Test
    @ProxyIgnore
    public void testMessageViaProxy() throws Exception {

        final WebSocketChannel webSocketChannel = WebSocketClient.connectionBuilder(worker, DefaultServer.getBufferPool(), new URI(DefaultServer.getDefaultServerURL()))
                .setProxyUri(new URI("http", null, DefaultServer.getHostAddress("default"), DefaultServer.getHostPort("default")  + 10, "/proxy", null, null))
                .connect().get();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        webSocketChannel.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                String data = message.getData();
                result.set(data);
                latch.countDown();
            }

            @Override
            protected void onError(WebSocketChannel channel, Throwable error) {
                super.onError(channel, error);
                error.printStackTrace();
                latch.countDown();
            }
        });
        webSocketChannel.resumeReceives();


        StreamSinkFrameChannel sendChannel = webSocketChannel.send(WebSocketFrameType.TEXT);
        new StringWriteChannelListener("Hello World").setup(sendChannel);

        latch.await(10, TimeUnit.SECONDS);
        Assert.assertEquals("Hello World", result.get());
        webSocketChannel.sendClose();
        Assert.assertEquals("CONNECT " + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default"), connectLog.poll());
    }


    @Test
    @ProxyIgnore
    public void testMessageViaWssProxy() throws Exception {


        final WebSocketChannel webSocketChannel = WebSocketClient.connectionBuilder(worker, DefaultServer.getBufferPool(), new URI(DefaultServer.getDefaultServerSSLAddress()))
                .setSsl(new UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY, DefaultServer.getClientSSLContext()))
                .setProxyUri(new URI("http", null, DefaultServer.getHostAddress("default"), DefaultServer.getHostPort("default") + 10, "/proxy", null, null))
                .connect().get();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        webSocketChannel.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                String data = message.getData();
                result.set(data);
                latch.countDown();
            }

            @Override
            protected void onError(WebSocketChannel channel, Throwable error) {
                super.onError(channel, error);
                error.printStackTrace();
                latch.countDown();
            }
        });
        webSocketChannel.resumeReceives();


        StreamSinkFrameChannel sendChannel = webSocketChannel.send(WebSocketFrameType.TEXT);
        new StringWriteChannelListener("Hello World").setup(sendChannel);

        latch.await(10, TimeUnit.SECONDS);
        Assert.assertEquals("Hello World", result.get());
        webSocketChannel.sendClose();
        Assert.assertEquals("CONNECT " + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostSSLPort("default"), connectLog.poll());
    }
}
