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

package io.undertow.server.handlers.caching;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.cache.CacheHandler;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.cache.ResponseCache;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests out the caching handler
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class CacheHandlerTestCase {

    @Test
    public void testBasicPathBasedCaching() throws IOException {

        final AtomicInteger responseCount = new AtomicInteger();

        final HttpHandler messageHandler = exchange -> {
            final ResponseCache cache = exchange.getAttachment(ResponseCache.ATTACHMENT_KEY);
            if (!cache.tryServeResponse()) {
                final String data = "Response " + responseCount.incrementAndGet();
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, data.length() + "");
                exchange.getResponseSender().send(data);
            }
        };
        final CacheHandler cacheHandler = new CacheHandler(new DirectBufferCache(100, 10, 1000), messageHandler);
        DefaultServer.setRootHandler(cacheHandler);

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            //it takes 5 hits to make an entry actually get cached
            for (int i = 1; i <= 5; ++i) {
                int index = i;
                client.execute(get, result -> {
                    Assert.assertEquals(StatusCodes.OK, result.getCode());
                    Assert.assertEquals("Response " + index, HttpClientUtils.readResponse(result));
                    return null;
                });
            }

            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("Response 5", HttpClientUtils.readResponse(result));
                return null;
            });

            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("Response 5", HttpClientUtils.readResponse(result));
                return null;
            });

            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("Response 5", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path2");

            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("Response 6", HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }
}
