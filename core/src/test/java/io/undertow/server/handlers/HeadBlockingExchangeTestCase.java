/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Test that {@code HEAD} requests can be used with a blocking exchange, setting response
 * content-length without unnecessarily writing bytes.
 *
 * @author Carter Kozak
 */
@RunWith(DefaultServer.class)
public class HeadBlockingExchangeTestCase {

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new BlockingHandler(
                exchange -> exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + 100)));
    }

    @Test
    public void sendHttpHead() throws IOException {
        HttpHead head = new HttpHead(DefaultServer.getDefaultServerURL());
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(head);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("", HttpClientUtils.readResponse(result));
            Assert.assertEquals("100", result.getFirstHeader("Content-Length").getValue());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
