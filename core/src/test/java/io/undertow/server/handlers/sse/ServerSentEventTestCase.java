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

package io.undertow.server.handlers.sse;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServerSentEventTestCase {


    @Test
    public void testSSE() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            DefaultServer.setRootHandler(new ServerSentEventHandler(new ServerSentEventConnectionCallback() {
                @Override
                public void connected(ServerSentEventConnection connection, String lastEventId) {
                    connection.send("msg 1", new ServerSentEventConnection.EventCallback() {
                        @Override
                        public void done(ServerSentEventConnection connection, String data, String event, String id) {
                            connection.send("msg 2", new ServerSentEventConnection.EventCallback() {
                                @Override
                                public void done(ServerSentEventConnection connection, String data, String event, String id) {
                                    IoUtils.safeClose(connection);
                                }

                                @Override
                                public void failed(ServerSentEventConnection connection, String data, String event, String id, IOException e) {
                                    e.printStackTrace();
                                    IoUtils.safeClose(connection);
                                }
                            });
                        }

                        @Override
                        public void failed(ServerSentEventConnection connection, String data, String event, String id, IOException e) {
                            e.printStackTrace();
                            IoUtils.safeClose(connection);
                        }
                    });
                }
            }));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);

            Assert.assertEquals("data:msg 1\n\ndata:msg 2\n\n", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testLargeMessage() throws IOException {
        TestHttpClient client = new TestHttpClient();
        final StringBuilder sb = new StringBuilder();
        for(int i =0; i < 10000; ++i) {
            sb.append("hello world ");
        }
        try {
            DefaultServer.setRootHandler(new ServerSentEventHandler(new ServerSentEventConnectionCallback() {
                @Override
                public void connected(ServerSentEventConnection connection, String lastEventId) {
                    connection.send(sb.toString(), new ServerSentEventConnection.EventCallback() {
                        @Override
                        public void done(ServerSentEventConnection connection, String data, String event, String id) {
                            IoUtils.safeClose(connection);
                        }

                        @Override
                        public void failed(ServerSentEventConnection connection, String data, String event, String id, IOException e) {
                            e.printStackTrace();
                            IoUtils.safeClose(connection);
                        }
                    });
                }
            }));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);

            Assert.assertEquals("data:" + sb.toString() + "\n\n", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testConnectionFail() throws IOException, InterruptedException {

        final Socket socket = new Socket(DefaultServer.getHostAddress("default"), DefaultServer.getHostPort("default"));
        final CountDownLatch latch = new CountDownLatch(1);
        DefaultServer.setRootHandler(new ServerSentEventHandler(new ServerSentEventConnectionCallback() {
            @Override
            public void connected(ServerSentEventConnection connection, String lastEventId) {
                while (connection.isOpen()) {
                    connection.send("hello", new ServerSentEventConnection.EventCallback() {
                        @Override
                        public void done(ServerSentEventConnection connection, String data, String event, String id) {
                        }

                        @Override
                        public void failed(ServerSentEventConnection connection, String data, String event, String id, IOException e) {
                            latch.countDown();
                        }
                    });
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }));
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        out.write(("GET / HTTP/1.1\r\n\r\n").getBytes());
        out.flush();
        out.close();
        in.close();
        if(!latch.await(10, TimeUnit.SECONDS)) {
            Assert.fail();
        }
    }
}
