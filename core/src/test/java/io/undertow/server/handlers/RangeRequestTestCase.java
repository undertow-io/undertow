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

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.resource.CachingResourceManager;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class RangeRequestTestCase {


    @BeforeClass
    public static void setup() throws URISyntaxException {
        Path rootPath = Paths.get(RangeRequestTestCase.class.getResource("range.txt").toURI()).getParent();
        PathHandler path = Handlers.path();
        path.addPrefixPath("/path", new ByteRangeHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseHeaders().put(Headers.LAST_MODIFIED, DateUtils.toDateString(new Date(10000)));
                exchange.getResponseHeaders().put(Headers.ETAG, "\"someetag\"");
                exchange.getResponseSender().send("0123456789");
            }
        }, true));
        path.addPrefixPath("/resource",  new ResourceHandler( new PathResourceManager(rootPath, 10485760))
                .setDirectoryListingEnabled(true));
        path.addPrefixPath("/cachedresource",  new ResourceHandler(new CachingResourceManager(1000, 1000000, new DirectBufferCache(1000, 10, 10000), new PathResourceManager(rootPath, 10485760), -1))
                .setDirectoryListingEnabled(true));
        DefaultServer.setRootHandler(path);
    }

    @Test
    public void testGenericRangeHandler() throws IOException, InterruptedException {
        runTest("/path", true);
    }
    @Test
    public void testResourceHandler() throws IOException, InterruptedException {
        runTest("/resource/range.txt", false);
    }
    @Test
    public void testCachedResourceHandler() throws IOException, InterruptedException {
        runTest("/cachedresource/range.txt", false);
    }

    public void runTest(String path, boolean etag) throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
            get.addHeader(Headers.RANGE_STRING, "bytes=2-3");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            String response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("23", response);
            Assert.assertEquals( "bytes 2-3/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
            get.addHeader(Headers.RANGE_STRING, "bytes=0-0");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("0", response);
            Assert.assertEquals( "bytes 0-0/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
            get.addHeader(Headers.RANGE_STRING, "bytes=1-");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("123456789", response);
            Assert.assertEquals( "bytes 1-9/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
            get.addHeader(Headers.RANGE_STRING, "bytes=0-");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("0123456789", response);
            Assert.assertEquals("bytes 0-9/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
            get.addHeader(Headers.RANGE_STRING, "bytes=9-");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("9", response);
            Assert.assertEquals("bytes 9-9/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
            get.addHeader(Headers.RANGE_STRING, "bytes=-1");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("9", response);
            Assert.assertEquals("bytes 9-9/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
            get.addHeader(Headers.RANGE_STRING, "bytes=99-100");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.REQUEST_RANGE_NOT_SATISFIABLE, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("", response);
            Assert.assertEquals("bytes */10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
            get = new HttpGet(DefaultServer.getDefaultServerURL() + path);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
            get.addHeader(Headers.RANGE_STRING, "bytes=2-1");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.REQUEST_RANGE_NOT_SATISFIABLE, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("", response);
            Assert.assertEquals("bytes */10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
            get.addHeader(Headers.RANGE_STRING, "bytes=2-3");
            get.addHeader(Headers.IF_RANGE_STRING, DateUtils.toDateString(new Date(System.currentTimeMillis() + 1000)));
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("23", response);
            Assert.assertEquals( "bytes 2-3/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
            get.addHeader(Headers.RANGE_STRING, "bytes=2-3");
            get.addHeader(Headers.IF_RANGE_STRING, DateUtils.toDateString(new Date(0)));
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("0123456789", response);
            Assert.assertNull(result.getFirstHeader(Headers.CONTENT_RANGE_STRING));

            if(etag) {

                get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
                get.addHeader(Headers.RANGE_STRING, "bytes=2-3");
                get.addHeader(Headers.IF_RANGE_STRING, "\"someetag\"");
                result = client.execute(get);
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
                response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("23", response);
                Assert.assertEquals( "bytes 2-3/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());

                get = new HttpGet(DefaultServer.getDefaultServerURL() + path);
                get.addHeader(Headers.RANGE_STRING, "bytes=2-3");
                get.addHeader(Headers.IF_RANGE_STRING, "\"otheretag\"");
                result = client.execute(get);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("0123456789", response);
                Assert.assertNull(result.getFirstHeader(Headers.CONTENT_RANGE_STRING));
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
