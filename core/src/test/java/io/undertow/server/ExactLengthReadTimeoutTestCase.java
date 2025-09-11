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
import java.io.OutputStream;

import io.undertow.server.handlers.BlockingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 *
 * Tests to ensure no read timeout after an exact read of the content-length
 *
 * @author Stuart Douglas
 * @author Flavia Rainone
 * @author Aaron Ogburn
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class ExactLengthReadTimeoutTestCase {

    private static volatile String message;

    private static final String DATA = "1234567890ABCDEF";

    private static final int DATA_MULTIPLE = 2048;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                try {
                    final OutputStream outputStream = exchange.getOutputStream();
                    final InputStream inputStream =  exchange.getInputStream();

                    long length = exchange.getRequestContentLength();
                    byte[] b = new byte[DATA_MULTIPLE * DATA.length()];
                    int i = 1;
                    StringBuilder builder = new StringBuilder();
                    // read exact content length
                    while (i > 0 && length > 0) {
                        i = inputStream.read(b);
                        if (i > 0) {
                           length -=i;
                           builder.append(new String(b, 0, i));
                        }
                    }

                    // this shouldn't cause timeout after complete read
                    try {
                        Thread.sleep(100);
                    }  catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    Assert.assertEquals(message, builder.toString());
                    inputStream.close();
                    outputStream.close();
                } catch (IOException e) {
                    exchange.getResponseHeaders().put(Headers.CONNECTION, "close");
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @DefaultServer.BeforeServerStarts
    public static void beforeClass() {
        DefaultServer.setServerOptions(OptionMap.create(Options.READ_TIMEOUT, 10));
    }

    @DefaultServer.AfterServerStops
    public static void afterClass() {
        DefaultServer.setServerOptions(OptionMap.EMPTY);
    }

    @Test
    public void testExactLengthReadTimeout() throws InterruptedException, IOException {
        StringBuilder builder = new StringBuilder(1000 * DATA.length());

        for (int i = 0; i < DATA_MULTIPLE; ++i) {
            try {
                builder.append(DATA);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }

        message = builder.toString();
        final TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.setEntity(new StringEntity(message));
            post.addHeader(Headers.CONNECTION_STRING, "close");
            boolean socketFailure = false;
            try {
                // Request should succeed.
                HttpResponse result = client.execute(post);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            } catch (NoHttpResponseException e) {
                Assert.fail("No response was received, this was presumably caused by read-timeout closing the connection.");
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
