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

import io.undertow.UndertowOptions;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Stuart Douglas
 */
@HttpOneOnly
@ProxyIgnore
@RunWith(DefaultServer.class)
public class MaxRequestSizeTestCase {

    public static final String A_MESSAGE = "A message";

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final OutputStream outputStream = exchange.getOutputStream();
                final InputStream inputStream = exchange.getInputStream();
                String m = HttpClientUtils.readResponse(inputStream);
                Assert.assertEquals(A_MESSAGE, m);
                inputStream.close();
                outputStream.close();
            }
        });
    }

    @Test
    public void testMaxRequestHeaderSize() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        final TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            post.setEntity(new StringEntity(A_MESSAGE));
            post.addHeader(Headers.CONNECTION_STRING, "close");
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            OptionMap maxSize = OptionMap.create(UndertowOptions.MAX_HEADER_SIZE, 10);
            DefaultServer.setUndertowOptions(maxSize);

            try {
                HttpResponse response = client.execute(post);
                HttpClientUtils.readResponse(response);

                Assert.assertEquals(StatusCodes.BAD_REQUEST, response.getStatusLine().getStatusCode());
            } catch (IOException e) {
                //expected
            }

            maxSize = OptionMap.create(UndertowOptions.MAX_HEADER_SIZE, 1000);
            DefaultServer.setUndertowOptions(maxSize);
            result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

        } finally {
            DefaultServer.setUndertowOptions(existing);
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testMaxRequestEntitySize() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        final TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            post.setEntity(new StringEntity(A_MESSAGE));
            post.addHeader(Headers.CONNECTION_STRING, "close");
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            OptionMap maxSize = OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, (long) A_MESSAGE.length() - 1);
            DefaultServer.setUndertowOptions(maxSize);

            post = new HttpPost(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            post.setEntity(new StringEntity(A_MESSAGE));
            result = client.execute(post);
            Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            maxSize = OptionMap.create(UndertowOptions.MAX_HEADER_SIZE, 1000);
            DefaultServer.setUndertowOptions(maxSize);
            post = new HttpPost(DefaultServer.getDefaultServerURL() + "/notamatchingpath");
            post.setEntity(new StringEntity(A_MESSAGE));
            post.addHeader(Headers.CONNECTION_STRING, "close");
            result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

        } finally {
            DefaultServer.setUndertowOptions(existing);
            client.getConnectionManager().shutdown();
        }
    }
}
