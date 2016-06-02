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

package io.undertow.server.handlers;

import io.undertow.io.IoCallback;
import io.undertow.io.Receiver;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ReceiverTestCase {

    public static final String HELLO_WORLD = "Hello World";

    private static final LinkedBlockingDeque<IOException> EXCEPTIONS = new LinkedBlockingDeque<>();
    public static final Receiver.ErrorCallback ERROR_CALLBACK = new Receiver.ErrorCallback() {
        @Override
        public void error(HttpServerExchange exchange, IOException e) {
            EXCEPTIONS.add(e);
            exchange.endExchange();
        }
    };

    @BeforeClass
    public static void setup() {
        HttpHandler testFullString = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {

                exchange.getRequestReceiver().receiveFullString(new Receiver.FullStringCallback() {
                    @Override
                    public void handle(HttpServerExchange exchange, String message) {
                        exchange.getResponseSender().send(message);
                    }
                }, ERROR_CALLBACK);
            }
        };

        HttpHandler testPartialString = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final StringBuilder sb = new StringBuilder();
                exchange.getRequestReceiver().receivePartialString(new Receiver.PartialStringCallback() {
                    @Override
                    public void handle(HttpServerExchange exchange, String message, boolean last) {
                        sb.append(message);
                        if(last) {
                            exchange.getResponseSender().send(sb.toString());
                        }
                    }
                }, ERROR_CALLBACK);
            }
        };

        HttpHandler testFullBytes = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.getRequestReceiver().receiveFullBytes(new Receiver.FullBytesCallback() {
                    @Override
                    public void handle(HttpServerExchange exchange, byte[] message) {
                        exchange.getResponseSender().send(ByteBuffer.wrap(message));
                    }
                }, ERROR_CALLBACK);
            }
        };

        HttpHandler testPartialBytes = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {

                class CB implements Receiver.PartialBytesCallback, IoCallback {

                    final Receiver receiver;
                    final Sender sender;

                    CB(Receiver receiver, Sender sender) {
                        this.receiver = receiver;
                        this.sender = sender;
                    }

                    @Override
                    public void onComplete(HttpServerExchange exchange, Sender sender) {
                        receiver.resume();
                    }

                    @Override
                    public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                        exception.printStackTrace();
                        exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                        exchange.endExchange();
                    }

                    @Override
                    public void handle(HttpServerExchange exchange, byte[] message, boolean last) {
                        receiver.pause();
                        sender.send(ByteBuffer.wrap(message), last ? IoCallback.END_EXCHANGE : this);
                    }
                }
                CB callback = new CB(exchange.getRequestReceiver(), exchange.getResponseSender());
                exchange.getRequestReceiver().receivePartialBytes(callback);
            }
        };
        final PathHandler handler = new PathHandler().addPrefixPath("/fullstring", testFullString)
                .addPrefixPath("/partialstring", testPartialString)
                .addPrefixPath("/fullbytes", testFullBytes)
                .addPrefixPath("/partialbytes", testPartialBytes);
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                Deque<String> block = exchange.getQueryParameters().get("blocking");
                if(block != null) {
                    exchange.startBlocking();
                    exchange.dispatch(handler);
                    return;
                }
                handler.handleRequest(exchange);
            }
        });
    }

    @Test
    public void testAsyncReceiveWholeString() {
        doTest("/fullstring");
    }

    @Test
    public void testAsyncReceivePartialString() {
        doTest("/partialstring");
    }

    @Test
    public void testAsyncReceiveWholeBytes() {
        doTest("/fullbytes");
    }

    @Test
    public void testAsyncReceiveWholeBytesFailed() throws Exception {
        EXCEPTIONS.clear();
        Socket socket = new Socket();
        socket.connect(DefaultServer.getDefaultServerAddress());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; ++i) {
            sb.append("hello world\r\n");
        }
        //send a large request that is too small, then kill the socket
        String request = "POST /fullbytes HTTP/1.1\r\nHost:localhost\r\nContent-Length:" + sb.length() + 100 + "\r\n\r\n" + sb.toString();
        OutputStream outputStream = socket.getOutputStream();

        outputStream.write(request.getBytes("US-ASCII"));
        socket.getInputStream().close();
        outputStream.close();

        IOException e = EXCEPTIONS.poll(2, TimeUnit.SECONDS);
        Assert.assertNotNull(e);

    }

    @Test
    public void testAsyncReceivePartialBytes() {
        doTest("/partialbytes");
    }

    @Test
    public void testBlockingReceiveWholeString() {
        doTest("/fullstring?blocking");
    }

    @Test
    public void testBlockingReceivePartialString() {
        doTest("/partialstring?blocking");
    }


    @Test
    public void testBlockingReceiveWholeBytes() {
        doTest("/fullbytes?blocking");
    }

    @Test
    public void testBlockingReceivePartialBytes() {
        doTest("/partialbytes?blocking");
    }
    public void doTest(String path) {
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 1000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, path);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    public void runTest(final String message, String url) throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            String uri = DefaultServer.getDefaultServerURL() + url;
            HttpPost post = new HttpPost(uri);
            post.setEntity(new StringEntity(message));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(message.length(), response.length());
            Assert.assertEquals(message, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
