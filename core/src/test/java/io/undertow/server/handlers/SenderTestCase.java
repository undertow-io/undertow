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

import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SenderTestCase {

    public static final int SENDS = 10000;
    public static final int TXS = 1000;
    public static final String HELLO_WORLD = "Hello World";

    @BeforeClass
    public static void setup() {
        HttpHandler lotsOfSendsHandler = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                boolean blocking = exchange.getQueryParameters().get("blocking").getFirst().equals("true");
                if (blocking) {
                    if (exchange.isInIoThread()) {
                        exchange.startBlocking();
                        exchange.dispatch(this);
                        return;
                    }
                }
                final Sender sender = exchange.getResponseSender();
                class SendClass implements Runnable, IoCallback {

                    int sent = 0;

                    @Override
                    public void run() {
                        sent++;
                        sender.send("a", this);
                    }

                    @Override
                    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                        if (sent++ == SENDS) {
                            sender.close();
                            return;
                        }
                        sender.send("a", this);
                    }

                    @Override
                    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                        exception.printStackTrace();
                        exchange.endExchange();
                    }
                }
                new SendClass().run();
            }
        };
        HttpHandler lotsOfTransferHandler = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {

                boolean blocking = exchange.getQueryParameters().get("blocking").getFirst().equals("true");
                if (blocking) {
                    if (exchange.isInIoThread()) {
                        exchange.startBlocking();
                        exchange.dispatch(this);
                        return;
                    }
                }
                URI uri = SenderTestCase.class.getResource(SenderTestCase.class.getSimpleName() + ".class").toURI();
                Path file = Paths.get(uri);
                final FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);

                exchange.setResponseContentLength(channel.size() * TXS);

                final Sender sender = exchange.getResponseSender();
                class SendClass implements Runnable, IoCallback {

                    int sent = 0;

                    @Override
                    public void run() {
                        sent++;
                        try {
                            channel.position(0);
                        } catch (IOException e) {
                        }
                        sender.transferFrom(channel, this);
                    }

                    @Override
                    public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                        if (sent++ == TXS) {
                            sender.close();
                            return;
                        }
                        try {
                            channel.position(0);
                        } catch (IOException e) {
                        }
                        sender.transferFrom(channel, this);
                    }

                    @Override
                    public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                        exception.printStackTrace();
                        exchange.endExchange();
                    }
                }
                new SendClass().run();
            }
        };

        final HttpHandler fixedLengthSender = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send(HELLO_WORLD);
            }
        };

        PathHandler handler = new PathHandler().addPrefixPath("/lots", lotsOfSendsHandler)
                .addPrefixPath("/fixed", fixedLengthSender)
                .addPrefixPath("/transfer", lotsOfTransferHandler);
        DefaultServer.setRootHandler(handler);
    }


    @Test
    public void testAsyncSender() throws IOException {
        StringBuilder sb = new StringBuilder(SENDS);
        for (int i = 0; i < SENDS; ++i) {
            sb.append("a");
        }
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/lots?blocking=false");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            Assert.assertEquals(sb.toString(), HttpClientUtils.readResponse(result));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @ProxyIgnore("UNDERTOW-1926 fails with proxy http2 sporadically") // FIXME
    public void testAsyncTransfer() throws Exception {
        StringBuilder sb = new StringBuilder(TXS);
        for (int i = 0; i < TXS; ++i) {
            sb.append("a");
        }
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/transfer?blocking=false");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Path file = Paths.get(SenderTestCase.class.getResource(SenderTestCase.class.getSimpleName() + ".class").toURI());
            long length = Files.size(file);
            byte[] data = new byte[(int) length * TXS];
            for (int i = 0; i < TXS; i++) {
                try(DataInputStream is = new DataInputStream(Files.newInputStream(file))) {
                    is.readFully(data, (int) (i * length), (int) length);
                }
            }
            Assert.assertArrayEquals(data, HttpClientUtils.readRawResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @ProxyIgnore("UNDERTOW-1926 fails with proxy http2 sporadically") // FIXME
    public void testSyncTransfer() throws Exception {
        StringBuilder sb = new StringBuilder(TXS);
        for (int i = 0; i < TXS; ++i) {
            sb.append("a");
        }
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/transfer?blocking=true");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Path file = Paths.get(SenderTestCase.class.getResource(SenderTestCase.class.getSimpleName() + ".class").toURI());
            long length = Files.size(file);
            byte[] data = new byte[(int) length * TXS];
            for (int i = 0; i < TXS; i++) {
                try(DataInputStream is = new DataInputStream(Files.newInputStream(file))) {
                    is.readFully(data, (int) (i * length), (int) length);
                }
            }
            Assert.assertArrayEquals(data, HttpClientUtils.readRawResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testBlockingSender() throws IOException {
        StringBuilder sb = new StringBuilder(SENDS);
        for (int i = 0; i < SENDS; ++i) {
            sb.append("a");
        }
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/lots?blocking=true");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            Assert.assertEquals(sb.toString(), HttpClientUtils.readResponse(result));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSenderSetsContentLength() throws IOException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/fixed");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(HELLO_WORLD, HttpClientUtils.readResponse(result));
            Header[] header = result.getHeaders(Headers.CONTENT_LENGTH_STRING);
            Assert.assertEquals(1, header.length);
            Assert.assertEquals("" + HELLO_WORLD.length(), header[0].getValue());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
