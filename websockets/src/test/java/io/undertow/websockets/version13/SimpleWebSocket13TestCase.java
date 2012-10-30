package io.undertow.websockets.version13;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.undertow.server.HttpServerExchange;
import io.undertow.test.utils.DefaultServer;
import io.undertow.util.StringReadChannelListener;
import io.undertow.util.StringWriteChannelListener;
import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.server.WebSocketConnectionCallback;
import io.undertow.websockets.server.WebSocketProtocolHandshakeHandler;
import junit.framework.Assert;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.client.WebSocketClientFactory;
import org.eclipse.jetty.websocket.core.api.UpgradeResponse;
import org.eclipse.jetty.websocket.core.api.WebSocketConnection;
import org.eclipse.jetty.websocket.core.api.WebSocketException;
import org.eclipse.jetty.websocket.core.api.WebSocketListener;
import org.junit.runner.RunWith;
import org.xnio.ChannelListener;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleWebSocket13TestCase {


    @org.junit.Test
    public void testBasicConnect() throws Exception {
        final AtomicBoolean connected = new AtomicBoolean(false);
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler("", "", new WebSocketConnectionCallback() {
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

        WebSocketClientFactory factory = new WebSocketClientFactory();
        factory.start();
        WebSocketClient client = factory.newWebSocketClient(new WebSocketListener() {
            @Override
            public void onWebSocketBinary(final byte[] payload, final int offset, final int len) {

            }

            @Override
            public void onWebSocketClose(final int statusCode, final String reason) {

            }

            @Override
            public void onWebSocketConnect(final WebSocketConnection connection) {
                try {
                    connection.write(null, new Callback<Object>() {
                        @Override
                        public void completed(final Object o) {
                            System.out.print("completed");
                        }

                        @Override
                        public void failed(final Object o, final Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    }, "hello");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onWebSocketException(final WebSocketException error) {

            }

            @Override
            public void onWebSocketText(final String message) {
                result.set(message);
                latch.countDown();
            }
        });
        FutureCallback<UpgradeResponse> future = client.connect(new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default")));
        future.get();
        latch.await();
        Assert.assertTrue(connected.get());
        Assert.assertEquals("world", result.get());
    }

}
