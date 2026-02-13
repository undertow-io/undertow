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

import io.undertow.server.ServerConnection;
import io.undertow.server.protocol.http.HttpAttachments;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import io.undertow.util.StringWriteChannelListener;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.impl.io.ChunkedInputStream;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class ChunkedResponseTrailersTestCase {

    private static final String MESSAGE = "My HTTP Request!";

    private static volatile String message;

    private static volatile ServerConnection connection;

    @Before
    public void reset() {
        connection = null;
    }

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(exchange -> {
            try {
                if (connection == null) {
                    connection = exchange.getConnection();
                } else if (!DefaultServer.isAjp() && !DefaultServer.isProxy() && connection != exchange.getConnection()) {
                    final OutputStream outputStream = exchange.getOutputStream();
                    outputStream.write("Connection not persistent".getBytes());
                    outputStream.close();
                    return;
                }
                HeaderMap trailers = new HeaderMap();
                exchange.putAttachment(HttpAttachments.RESPONSE_TRAILERS, trailers);
                trailers.put(HttpString.tryFromString("foo"), "fooVal");
                trailers.put(HttpString.tryFromString("bar"), "barVal");
                new StringWriteChannelListener(message).setup(exchange.getResponseChannel());

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void sendHttpRequest() throws Exception {
        Assume.assumeFalse(DefaultServer.isH2()); //this test will still run under h2-upgrade, but will fail

        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
        final AtomicReference<ChunkedInputStream> stream = new AtomicReference<>();

        try (CloseableHttpClient client = TestHttpClient.custom().addResponseInterceptorLast((response, entityDetails, context) -> {
            HttpEntity entity = ((ClassicHttpResponse) response).getEntity();
            if (entity != null) {
                InputStream instream = entity.getContent();
                if (instream instanceof ChunkedInputStream) {
                    stream.set(((ChunkedInputStream) instream));
                }
            }
        }).build()) {
            generateMessage(1);
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());

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
                return null;
            });

            generateMessage(1000);
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
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
                return null;
            });
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
