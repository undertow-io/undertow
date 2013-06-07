/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.server.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

import io.undertow.conduits.ChunkedStreamSinkConduit;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.StringWriteChannelListener;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.io.ChunkedInputStream;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@AjpIgnore
public class ChunkedResponseTrailersTestCase {

    private static final String MESSAGE = "My HTTP Request!";

    private static volatile String message;

    private static volatile HttpServerConnection connection;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                try {
                    if (connection == null) {
                        connection = exchange.getConnection();
                    } else if (!DefaultServer.isAjp() && connection.getChannel() != exchange.getConnection().getChannel()) {
                        final OutputStream outputStream = exchange.getOutputStream();
                        outputStream.write("Connection not persistent".getBytes());
                        outputStream.close();
                        return;
                    }
                    HeaderMap trailers = new HeaderMap();
                    exchange.putAttachment(ChunkedStreamSinkConduit.TRAILERS, trailers);
                    trailers.put(HttpString.tryFromString("foo"), "fooVal");
                    trailers.put(HttpString.tryFromString("bar"), "barVal");
                    new StringWriteChannelListener(message).setup(exchange.getResponseChannel());

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void sendHttpRequest() throws Exception {

        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        TestHttpClient client = new TestHttpClient();
        final AtomicReference<ChunkedInputStream> stream = new AtomicReference<ChunkedInputStream>();
        client.addResponseInterceptor(new HttpResponseInterceptor() {

            public void process( final HttpResponse response, final HttpContext context) throws IOException {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    InputStream instream = entity.getContent();
                    if (instream instanceof ChunkedInputStream) {
                        stream.set(((ChunkedInputStream) instream));
                    }
                }
            }
        });
        try {
            generateMessage(1);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());

            Assert.assertEquals(message, HttpClientUtils.readResponse(result));

            Header[] footers = stream.get().getFooters();
            Assert.assertEquals(2, footers.length);
            for (final Header header : footers) {
                if (header.getName().equals("foo")) {
                    Assert.assertEquals("fooVal", header.getValue());
                } else if (header.getName().equals("bar")) {
                    Assert.assertEquals("barVal", header.getValue());
                } else {
                    Assert.fail("Unknown header" + header);
                }
            }

            generateMessage(1000);
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals(message, HttpClientUtils.readResponse(result));
            footers = stream.get().getFooters();
            Assert.assertEquals(2, footers.length);
            for (final Header header : footers) {
                if (header.getName().equals("foo")) {
                    Assert.assertEquals("fooVal", header.getValue());
                } else if (header.getName().equals("bar")) {
                    Assert.assertEquals("barVal", header.getValue());
                } else {
                    Assert.fail("Unknown header" + header);
                }
            }
        } finally {
            client.getConnectionManager().shutdown();
        }

    }


    private static void generateMessage(int repetitions) {
        final StringBuilder builder = new StringBuilder(repetitions * MESSAGE.length());
        for (int i = 0; i < repetitions; ++i) {
            builder.append(MESSAGE);
        }
        message = builder.toString();
    }
}
