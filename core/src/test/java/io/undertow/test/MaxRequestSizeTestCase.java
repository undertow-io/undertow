/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package io.undertow.test;

import java.io.IOException;

import io.undertow.UndertowOptions;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.blocking.BlockingHandler;
import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Headers;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class MaxRequestSizeTestCase {

    public static final String A_MESSAGE = "A message";

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = DefaultServer.newBlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new BlockingHttpHandler() {
            @Override
            public void handleRequest(final BlockingHttpServerExchange exchange) {
                try {
                    String m = HttpClientUtils.readResponse(exchange.getInputStream());
                    Assert.assertEquals(A_MESSAGE, m);
                    exchange.getInputStream().close();
                    exchange.getOutputStream().close();
                } catch (IOException e) {
                    try {
                        exchange.getExchange().getResponseHeaders().put(Headers.CONNECTION, "close");
                        exchange.getExchange().setResponseCode(500);
                    } catch (Exception ignore) {

                    }
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void testMaxRequestHeaderSize() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        try {
            final DefaultHttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerAddress() + "/notamatchingpath");
            post.setEntity(new StringEntity(A_MESSAGE));
            post.addHeader(Headers.CONNECTION, "close");
            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            OptionMap maxSize = OptionMap.create(UndertowOptions.MAX_HEADER_SIZE, 10);
            DefaultServer.setUndertowOptions(maxSize);

            try {
                client.execute(post);
                Assert.fail("request should have been too big");
            } catch (IOException e) {
                //expected
            }

            maxSize = OptionMap.create(UndertowOptions.MAX_HEADER_SIZE, 1000);
            DefaultServer.setUndertowOptions(maxSize);
            result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

        } finally {
            DefaultServer.setUndertowOptions(existing);
        }
    }

    @Test
    public void testMaxRequestEntitySize() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        try {
            final DefaultHttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerAddress() + "/notamatchingpath");
            post.setEntity(new StringEntity(A_MESSAGE));
            post.addHeader(Headers.CONNECTION, "close");
            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            OptionMap maxSize = OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, (long) A_MESSAGE.length() - 1);
            DefaultServer.setUndertowOptions(maxSize);

            post = new HttpPost(DefaultServer.getDefaultServerAddress() + "/notamatchingpath");
            post.setEntity(new StringEntity(A_MESSAGE));
            result = client.execute(post);
            Assert.assertEquals(500, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            maxSize = OptionMap.create(UndertowOptions.MAX_HEADER_SIZE, 1000);
            DefaultServer.setUndertowOptions(maxSize);
            post = new HttpPost(DefaultServer.getDefaultServerAddress() + "/notamatchingpath");
            post.setEntity(new StringEntity(A_MESSAGE));
            post.addHeader(Headers.CONNECTION, "close");
            result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

        } finally {
            DefaultServer.setUndertowOptions(existing);
        }
    }
}
