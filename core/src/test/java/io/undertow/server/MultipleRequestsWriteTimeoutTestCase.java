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

package io.undertow.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.HttpString;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HTTP;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.WriteTimeoutException;

/**
 * This class tests the write timeout functionality for cases where there are multiple requests over the same connection.
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class MultipleRequestsWriteTimeoutTestCase {

    private static final Logger log = Logger.getLogger(MultipleRequestsWriteTimeoutTestCase.class);
    private static final Integer WRITE_TIMEOUT_VALUE = 100; // ms

    private IOException exception;
    private CountDownLatch transferComplete;

    @DefaultServer.BeforeServerStarts
    public static void setup() {
        DefaultServer.setServerOptions(OptionMap.builder()
                .set(Options.WRITE_TIMEOUT, WRITE_TIMEOUT_VALUE)
                .getMap());
    }

    @DefaultServer.AfterServerStops
    public static void cleanup() {
        DefaultServer.setServerOptions(OptionMap.EMPTY);
    }

    /**
     * Tests multiple HTTP requests over the same keep-alive connection. Time-out should not happen as each request
     * finishes in time, although there is a delay between the requests.
     */
    @Test
    public void testWriteTimeout() throws IOException, InterruptedException {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) {
                transferComplete = new CountDownLatch(1);

                final int contentLength = 8 * 1024;

                final ByteBuffer buffer = ByteBuffer.allocateDirect(contentLength);
                for (int i = 0; i < contentLength; ++i) {
                    buffer.put((byte) '*');
                }
                buffer.flip();

                exchange.getResponseHeaders().add(HttpString.tryFromString("Content-Length"), contentLength);
                exchange.getResponseSender().send(buffer, new IoCallback() {
                    @Override
                    public void onComplete(HttpServerExchange exchange, Sender sender) {
                        transferComplete.countDown();
                    }

                    @Override
                    public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                        log.errorf(exception, "Exception thrown during writing response.");
                        MultipleRequestsWriteTimeoutTestCase.this.exception = exception;
                        transferComplete.countDown();
                    }
                });
            }
        });

        // Call two subsequent requests over keep-alive connection.
        try (PoolingHttpClientConnectionManager basicConnManager = new PoolingHttpClientConnectionManager()) {
            try (CloseableHttpClient client = HttpClients.custom()
                    .setConnectionManager(basicConnManager)
                    .setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE)
                    .build()) {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
                get.setHeader(HTTP.CONN_KEEP_ALIVE, "timeout=5");

                log.infof("Request 1");
                CloseableHttpResponse response = client.execute(get);
                readContent(response);

                // Delay a bit. If the write timeout measuring is not reset in between requests, the second request would time out.
                Thread.sleep(WRITE_TIMEOUT_VALUE * 3L);

                log.infof("Request 2");
                response = client.execute(get);
                readContent(response);
            }
        }
        assertSuccess();
    }

    /**
     * This test simulates remoting connection - there is only one HTTP request, but the server is suspending and
     * resuming writes. Again, time-out should not happen, as each transmission finishes in time.
     */
    @Test
    public void testWriteTimeoutOnEjbLikeRequests() throws IOException, InterruptedException {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) {
                transferComplete = new CountDownLatch(1);

                final int capacity = 1024;

                final ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
                for (int i = 0; i < capacity; ++i) {
                    buffer.put((byte) '*');
                }
                buffer.flip();

                StreamSinkChannel responseChannel = exchange.getResponseChannel();
                responseChannel.getWriteSetter().set(channel -> {
                    try {
                        channel.write(buffer.duplicate());
                        channel.flush();
                        channel.suspendWrites();

                        // The suspendWrites() above should reset the timeout counter, so this delay should not trigger a timeout.
                        Thread.sleep(WRITE_TIMEOUT_VALUE * 3L);

                        // Second write should have the timeout counting from 0.
                        channel.write(buffer.duplicate());
                        channel.flush();
                        channel.suspendWrites();

                        exchange.endExchange();
                    } catch (IOException e) {
                        exception = e;
                    } catch (InterruptedException ignore) {
                    } finally {
                        transferComplete.countDown();
                    }
                });
                responseChannel.resumeWrites();
            }
        });

        try (PoolingHttpClientConnectionManager basicConnManager = new PoolingHttpClientConnectionManager()) {
            try (CloseableHttpClient client = HttpClients.custom()
                    .setConnectionManager(basicConnManager)
                    .setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE)
                    .build()) {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
                CloseableHttpResponse response = client.execute(get);
                readContent(response);
            }
        }
        assertSuccess();
    }

    private void readContent(CloseableHttpResponse response) {
        byte[] buffer = new byte[512];
        int read;
        try {
            InputStream content = response.getEntity().getContent();
            while ((read = content.read(buffer)) > 0) {
                log.debugf("Read %d bytes", read);
            }
        } catch (IOException e) {
            // Report the exception but don't fail. There could be chunking exceptions if the timeout did happen,
            // which we are not strictly interested in, although it signifies time out was triggered incorrectly.
            log.error(e);
        }
    }

    private void assertSuccess() throws IOException, InterruptedException {
        // Make sure server is done writing data.
        boolean latchValue = transferComplete.await(2, TimeUnit.SECONDS);
        Assert.assertTrue("Server writing didn't finish.", latchValue);

        // If writing timed out, a ClosedChannelException or WriteTimeoutException exception would have been captured in
        // the exception variable.
        if (exception instanceof ClosedChannelException || exception instanceof WriteTimeoutException) {
            Assert.fail("The connection timed out, while it shouldn't have.");
        } else if (exception != null) {
            // This is something unexpected - test errored.
            throw exception;
        }
    }
}
