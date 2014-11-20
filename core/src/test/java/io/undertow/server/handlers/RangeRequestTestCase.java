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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class RangeRequestTestCase {


    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new ByteRangeHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send("0123456789");
            }
        }, true));
    }

    @Test
    public void testRangeRequests() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("range", "bytes=2-3");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            String response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("23", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("range", "bytes=0-0");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("0", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("range", "bytes=1-");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("123456789", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("range", "bytes=9-");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("9", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("range", "bytes=-1");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            response = EntityUtils.toString(result.getEntity());
            Assert.assertEquals("9", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
