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
package io.undertow.websockets.jsr.test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpointConfig;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.NetworkUtils;
import io.undertow.websockets.jsr.JsrWebSocketFilter;
import io.undertow.websockets.jsr.ServerWebSocketContainer;
import io.undertow.websockets.jsr.UndertowSession;
import io.undertow.websockets.jsr.test.annotated.AnnotatedClientEndpoint;
import io.undertow.websockets.utils.FrameChecker;
import io.undertow.websockets.utils.WebSocketTestClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.xnio.FutureResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.xnio.IoFuture.Status.DONE;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@RunWith(DefaultServer.class)
@AjpIgnore
@HttpOneOnly
public class JsrWebSocketServer07Test {

    private static DeploymentManager deploymentManager;

    @org.junit.Test
    public void testBinaryWithByteBuffer() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<Throwable> cause = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();

        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
                session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                    @Override
                    public void onMessage(ByteBuffer message) {
                        ByteBuffer buf = ByteBuffer.allocate(message.remaining());
                        buf.put(message);
                        buf.flip();
                        session.getAsyncRemote().sendBinary(buf);
                    }
                });
            }
        }

        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());
        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + NetworkUtils.formatPossibleIpv6Address(DefaultServer.getHostAddress("default")) + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(BinaryWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();
        assertNull(cause.get());
        client.destroy();
    }

    @org.junit.Test
    public void testBinaryWithByteArray() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<Throwable> cause = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();
        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
                session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                    @Override
                    public void onMessage(byte[] message) {
                        session.getAsyncRemote().sendBinary(ByteBuffer.wrap(message.clone()));
                    }
                });
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);
        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());

        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(BinaryWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();
        assertNull(cause.get());
        client.destroy();
    }

    @org.junit.Test
    public void testText() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<Throwable> cause = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();

        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String message) {
                        session.getAsyncRemote().sendText(message);
                    }
                });
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);
        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());
        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new TextWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(TextWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();
        assertNull(cause.get());
        client.destroy();
    }

    @org.junit.Test
    public void testBinaryWithByteBufferByCompletion() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<SendResult> sendResult = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();
        final FutureResult<Void> latch2 = new FutureResult<>();

        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
                session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                    @Override
                    public void onMessage(ByteBuffer message) {
                        ByteBuffer buf = ByteBuffer.allocate(message.remaining());
                        buf.put(message);
                        buf.flip();
                        session.getAsyncRemote().sendBinary(buf, new SendHandler() {
                            @Override
                            public void onResult(SendResult result) {
                                sendResult.set(result);
                                if (result.getException() != null) {
                                    latch2.setException(new IOException(result.getException()));
                                } else {
                                    latch2.setResult(null);
                                }
                            }
                        });
                    }
                });
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());
        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(BinaryWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();
        assertEquals(DONE, latch2.getIoFuture().await());

        SendResult result = sendResult.get();
        Assert.assertNotNull(result);
        assertNull(result.getException());

        client.destroy();
    }

    @org.junit.Test
    public void testTextByCompletion() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<SendResult> sendResult = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();
        final FutureResult<Void> latch2 = new FutureResult<>();

        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String message) {
                        session.getAsyncRemote().sendText(message, result -> {
                            sendResult.set(result);
                            if (result.getException() != null) {
                                latch2.setException(new IOException(result.getException()));
                            } else {
                                latch2.setResult(null);
                            }
                        });
                    }
                });
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());

        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new TextWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(TextWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();
        assertEquals(DONE, latch2.getIoFuture().await());

        SendResult result = sendResult.get();
        Assert.assertNotNull(result);
        assertNull(result.getException());

        client.destroy();
    }

    @org.junit.Test
    public void testBinaryWithByteBufferByFuture() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<Future<Void>> sendResult = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();

        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
                session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
                    @Override
                    public void onMessage(ByteBuffer message) {
                        ByteBuffer buf = ByteBuffer.allocate(message.remaining());
                        buf.put(message);
                        buf.flip();
                        sendResult.set(session.getAsyncRemote().sendBinary(buf));
                    }
                });
            }

        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());
        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(BinaryWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().get();

        Future<Void> result = sendResult.get();
        /* FIXME result.wait(4000);
        assertTrue(result.isDone());*/

        client.destroy();
    }

    @org.junit.Test
    public void testTextByFuture() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<Future<Void>> sendResult = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();

        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String message) {
                        sendResult.set(session.getAsyncRemote().sendText(message));
                    }
                });
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);
        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());
        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new TextWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(TextWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();

        sendResult.get();
                /* FIXME .wait(4000);
        assertTrue(sendResult.get().isDone());*/

        client.destroy();
    }

    @org.junit.Test
    public void testBinaryWithByteArrayUsingStream() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<Throwable> cause = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();
        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
                session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                    @Override
                    public void onMessage(final byte[] message) {
                        DefaultServer.getWorker().execute(() -> {
                            try {
                                OutputStream out = session.getBasicRemote().getSendStream();
                                out.write(message);
                                out.flush();
                                out.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                                cause.set(e);
                                latch.setException(e);
                            }
                        });
                    }
                });
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());

        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(BinaryWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();
        assertNull(cause.get());
        client.destroy();
    }

    @org.junit.Test
    public void testTextUsingWriter() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<Throwable> cause = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();

        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        DefaultServer.getWorker().execute(() -> {
                            try {
                                Writer writer = session.getBasicRemote().getSendWriter();
                                writer.write(message);
                                writer.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                                cause.set(e);
                                latch.setException(e);
                            }
                        });
                    }
                });
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());
        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new TextWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(TextWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();
        assertNull(cause.get());
        client.destroy();
    }

    @org.junit.Test
    public void testPingPong() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<Throwable> cause = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();

        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());
        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new PingWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(PongWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();
        assertNull(cause.get());
        client.destroy();
    }

    @org.junit.Test
    public void testCloseFrame() throws Exception {
        final int code = 1000;
        final String reasonText = "TEST";
        final AtomicReference<CloseReason> reason = new AtomicReference<>();
        ByteBuffer payload = ByteBuffer.allocate(reasonText.length() + 2);
        payload.putShort((short) code);
        payload.put(reasonText.getBytes(StandardCharsets.UTF_8));
        payload.flip();

        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();
        final CountDownLatch clientLatch = new CountDownLatch(1);
        final AtomicInteger closeCount = new AtomicInteger();

        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
            }

            @Override
            public void onClose(Session session, CloseReason closeReason) {
                closeCount.incrementAndGet();
                reason.set(closeReason);
                clientLatch.countDown();
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());
        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new CloseWebSocketFrame(code, reasonText), new FrameChecker(CloseWebSocketFrame.class, payload.array(), latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();
        clientLatch.await();
        assertEquals(code, reason.get().getCloseCode().getCode());
        assertEquals(reasonText, reason.get().getReasonPhrase());
        assertEquals(1, closeCount.get());
        client.destroy();
    }

    /**
     * Section 5.5.1 of RFC 6455 says the reason body is optional
     */
    @org.junit.Test
    public void testCloseFrameWithoutReasonBody() throws Exception {
        final int code = 1000;
        final AtomicReference<CloseReason> reason = new AtomicReference<>();
        ByteBuffer payload = ByteBuffer.allocate(2);
        payload.putShort((short) code);
        payload.flip();

        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();
        final CountDownLatch clientLatch = new CountDownLatch(1);
        final AtomicInteger closeCount = new AtomicInteger();

        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
            }

            @Override
            public void onClose(Session session, CloseReason closeReason) {
                closeCount.incrementAndGet();
                reason.set(closeReason);
                clientLatch.countDown();
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());
        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new CloseWebSocketFrame(code, null), new FrameChecker(CloseWebSocketFrame.class, payload.array(), latch));
        assertEquals(DONE, latch.getIoFuture().await(10, TimeUnit.SECONDS));
        clientLatch.await();
        assertEquals(code, reason.get().getCloseCode().getCode());
        assertEquals("", reason.get().getReasonPhrase());
        assertEquals(1, closeCount.get());
        client.destroy();
    }

    @org.junit.Test
    public void testBinaryWithByteBufferAsync() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<Throwable> cause = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();

        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
                session.addMessageHandler(new MessageHandler.Partial<ByteBuffer>() {
                    @Override
                    public void onMessage(ByteBuffer message, boolean last) {
                        Assert.assertTrue(last);
                        ByteBuffer buf = ByteBuffer.allocate(message.remaining());
                        buf.put(message);
                        buf.flip();
                        session.getAsyncRemote().sendBinary(buf);
                    }
                });
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());
        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(BinaryWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();
        assertNull(cause.get());
        client.destroy();
    }

    @org.junit.Test
    public void testTextAsync() throws Exception {
        final byte[] payload = "payload".getBytes();
        final AtomicReference<Throwable> cause = new AtomicReference<>();
        final AtomicBoolean connected = new AtomicBoolean(false);
        final FutureResult<?> latch = new FutureResult<>();
        class TestEndPoint extends Endpoint {
            @Override
            public void onOpen(final Session session, EndpointConfig config) {
                connected.set(true);
                session.addMessageHandler(new MessageHandler.Partial<String>() {

                    final StringBuilder sb = new StringBuilder();

                    @Override
                    public void onMessage(String message, boolean last) {
                        sb.append(message);
                        if (!last) {
                            return;
                        }
                        session.getAsyncRemote().sendText(sb.toString());
                    }
                });
            }
        }
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(TestEndPoint.class, "/").configurator(new InstanceConfigurator(new TestEndPoint())).build());
        deployServlet(builder);

        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new TextWebSocketFrame(Unpooled.wrappedBuffer(payload)), new FrameChecker(TextWebSocketFrame.class, payload, latch));
        //FIXME UNDERTOW-1862 assertEquals(DONE, latch.getIoFuture().await());
        latch.getIoFuture().await();
        assertNull(cause.get());
        client.destroy();
    }


    // FIXME UNDERTOW-1862 @Test
    public void testErrorHandling() throws Exception {
        ServerWebSocketContainer builder = new ServerWebSocketContainer(TestClassIntrospector.INSTANCE, DefaultServer.getWorkerSupplier(), DefaultServer.getBufferPool(), Collections.emptyList(), false, false);

        builder.addEndpoint(ServerEndpointConfig.Builder.create(ProgramaticErrorEndpoint.class, "/").configurator(new InstanceConfigurator(new ProgramaticErrorEndpoint())).build());
        deployServlet(builder);

        AnnotatedClientEndpoint c = new AnnotatedClientEndpoint();

        Session session = ContainerProvider.getWebSocketContainer().connectToServer(c, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/"));
        assertEquals("hi", ProgramaticErrorEndpoint.getMessage());
        session.getAsyncRemote().sendText("app-error");
        assertEquals("app-error", ProgramaticErrorEndpoint.getMessage());
        assertEquals("ERROR: java.lang.RuntimeException", ProgramaticErrorEndpoint.getMessage());
        assertTrue(c.isOpen());

        session.getBasicRemote().sendText("io-error");
        assertEquals("io-error", ProgramaticErrorEndpoint.getMessage());
        assertEquals("ERROR: java.lang.RuntimeException", ProgramaticErrorEndpoint.getMessage());
        assertTrue(c.isOpen());
        ((UndertowSession) session).forceClose();
        assertEquals("CLOSED", ProgramaticErrorEndpoint.getMessage());
    }

    protected WebSocketVersion getVersion() {
        return WebSocketVersion.V07;
    }

    private void deployServlet(final ServerWebSocketContainer deployment) throws ServletException {
      assertNull("Can only be invoked once per method test", deploymentManager);
        final DeploymentInfo builder;
        builder = new DeploymentInfo()
                .setClassLoader(getClass().getClassLoader())
                .setContextPath("/")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("websocket.war")
                .addFilter(new FilterInfo("filter", JsrWebSocketFilter.class))
                .addFilterUrlMapping("filter", "/*", DispatcherType.REQUEST)
                .addServletContextAttribute(jakarta.websocket.server.ServerContainer.class.getName(), deployment);

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();
        deploymentManager = container.addDeployment(builder);
        deploymentManager.deploy();
        root.addPrefixPath(builder.getContextPath(), deploymentManager.start());

        DefaultServer.setRootHandler(root);
    }

    @After
    public void cleanup() throws ServletException {
        if (deploymentManager != null) {
            deploymentManager.stop();
            deploymentManager.undeploy();
            deploymentManager = null;
        }
    }

    private static class InstanceConfigurator extends ServerEndpointConfig.Configurator {

        private final Object endpoint;

        private InstanceConfigurator(final Object endpoint) {
            this.endpoint = endpoint;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getEndpointInstance(final Class<T> endpointClass) {
            return (T) endpoint;
        }
    }

}
