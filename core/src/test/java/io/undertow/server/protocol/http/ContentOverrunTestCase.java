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

package io.undertow.server.protocol.http;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
@HttpOneOnly
public class ContentOverrunTestCase {

    @BeforeClass
    public static void setup() {
        HttpHandler overlyLong = exchange -> {
            exchange.setResponseContentLength(10);
            exchange.getOutputStream().write("Overly long content".getBytes(StandardCharsets.UTF_8));
        };
        HttpHandler responseNotAllowed = exchange -> {
            exchange.setStatusCode(204);
            exchange.getOutputStream().write("Overly long content".getBytes(StandardCharsets.UTF_8));
        };

        DefaultServer.setRootHandler(Handlers.path().addPrefixPath("/204", new BlockingHandler(responseNotAllowed)).addPrefixPath("/long", new BlockingHandler(overlyLong)));
    }

    @Test
    public void testContentOn204() throws Exception {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/204");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.NO_CONTENT, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("", response);
                return null;
            });
        }
    }

    @Test
    public void testContentPastContentLength() throws Exception {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/long");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("Overly lon", response);
                return null;
            });
        }
    }
}
