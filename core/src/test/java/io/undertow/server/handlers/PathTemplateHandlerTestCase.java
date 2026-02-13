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
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class PathTemplateHandlerTestCase {

    @BeforeClass
    public static void setup() {
        HttpHandler handler = Handlers.pathTemplate()
                .add("/", exchange -> exchange.getResponseSender().send("root"))
                .add("/foo", exchange -> exchange.getResponseSender().send("foo"))
                .add("/foo/", exchange -> exchange.getResponseSender().send("foo/"))
                .add("/foo/{bar}", exchange -> exchange.getResponseSender()
                        .send("foo-path" + exchange.getQueryParameters().get("bar")));
        DefaultServer.setRootHandler(Handlers.path(handler).addPrefixPath("/prefix", handler));
    }

    @Test
    public void testPathTemplateHandler() throws IOException {
        runPathTemplateHandlerTest("");
    }

    @Test
    public void testPathTemplateHandlerWithPrefix() throws IOException {
        runPathTemplateHandlerTest("/prefix");
    }

    public void runPathTemplateHandlerTest(String prefix) throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix + "/foo");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("foo", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix + "/foo/");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("foo/", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix + "/foo/a");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("foo-path[a]", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix);
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("root", HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }
}
