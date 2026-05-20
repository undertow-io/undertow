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

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleNonBlockingServerTestCase {


    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new SetHeaderHandler(exchange ->
                exchange.getResponseSender().send("hi all"), "MyHeader", "MyValue"));
    }

    @Test
    public void sendHttpRequest() throws IOException, InterruptedException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders("MyHeader");
                Assert.assertEquals("MyValue", header[0].getValue());
                return null;
            });
        }
    }

    @Test
    public void sendHttp11RequestWithClose() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("Connection", "close");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders("MyHeader");
                Assert.assertEquals("MyValue", header[0].getValue());
                return null;
            });
        }
    }

    @Test
    public void sendHttpOneZeroRequest() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            ClassicHttpRequest get = ClassicRequestBuilder.get(DefaultServer.getDefaultServerURL() + "/path")
                    .setVersion(HttpVersion.HTTP_1_0).build();
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders("MyHeader");
                Assert.assertEquals("MyValue", header[0].getValue());
                return null;
            });
        }
    }
}
