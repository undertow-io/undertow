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
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
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
public class RoutingHandlerTestCase {

    @BeforeClass
    public static void setup() {
        RoutingHandler commonHandler = Handlers.routing()
                .add(Methods.GET, "/baz", exchange -> exchange.getResponseSender().send("baz"))
                .add(Methods.GET, "/baz/{foo}", exchange -> exchange.getResponseSender().send("baz-path" + exchange.getQueryParameters().get("foo")));

        RoutingHandler convienceHandler = Handlers.routing()
                .get("/bar", exchange -> exchange.getResponseSender().send("GET bar"))
                .put("/bar", exchange -> exchange.getResponseSender().send("PUT bar"))
                .post("/bar", exchange -> exchange.getResponseSender().send("POST bar"))
                .delete("/bar", exchange -> exchange.getResponseSender().send("DELETE bar"));

        HttpHandler handler = Handlers.routing()
                .add(Methods.GET, "/wild/{test}/*", exchange -> exchange.getResponseSender().send("wild:" + exchange.getQueryParameters().get("test") + ":" + exchange.getQueryParameters().get("*")))
                .add(Methods.GET, "/wilder/*", exchange -> exchange.getResponseSender().send("wilder:" + exchange.getQueryParameters().get("*")))
                .add(Methods.GET, "/wildest*", exchange -> exchange.getResponseSender().send("wildest:" + exchange.getQueryParameters().get("*")))
                .add(Methods.GET, "/foo", exchange -> exchange.getResponseSender().send("foo"))
                .add(Methods.GET, "/foo", Predicates.parse("contains[value=%{i,SomeHeader},search='special'] "), exchange -> exchange.getResponseSender().send("special foo"))
                .add(Methods.POST, "/foo", exchange -> exchange.getResponseSender().send("posted foo"))
                .add(Methods.POST, "/foo/{baz}", exchange -> exchange.getResponseSender().send("foo-path" + exchange.getQueryParameters().get("bar")))
                .add(Methods.GET, "/foo/{bar}", exchange -> exchange.getResponseSender().send("foo-path" + exchange.getQueryParameters().get("bar")))
                .get("/", exchange -> exchange.getResponseSender().send("GET /")).add(Methods.GET, "scoop/{scoop}", exchange -> exchange.getResponseSender().send("SCOOP GET"))
                .add(Methods.POST, "scoop/{scoop}", exchange -> exchange.getResponseSender().send("SCOOP POST"))
                .addAll(commonHandler)
                .addAll(convienceHandler);

        DefaultServer.setRootHandler(Handlers.path(handler).addPrefixPath("/prefix", handler));
    }

    @Test
    public void testRoutingTemplateHandler() throws IOException {
        runRoutingTemplateHandlerTests("");
    }

    @Test
    public void testRoutingTemplateHandlerWithPrefixPath() throws IOException {
        runRoutingTemplateHandlerTests("/prefix");
    }

    public void runRoutingTemplateHandlerTests(String prefix) throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix + "/foo");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("foo", HttpClientUtils.readResponse(result));
                return null;
            });

            HttpDelete delete = new HttpDelete(DefaultServer.getDefaultServerURL() + prefix + "/foo");
            client.execute(delete, result -> {
                Assert.assertEquals(StatusCodes.METHOD_NOT_ALLOWED, result.getCode());
                Assert.assertEquals("", HttpClientUtils.readResponse(result));
                return null;
            });

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + prefix + "/foo");
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("posted foo", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix + "/foo");
            get.addHeader("SomeHeader", "value");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("foo", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix + "/foo");
            get.addHeader("SomeHeader", "special");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("special foo", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix + "/foo/a");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("foo-path[a]", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix + "/baz");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("baz", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix + "/baz/a");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("baz-path[a]", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix + "/bar");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("GET bar", HttpClientUtils.readResponse(result));
                return null;
            });

            post = new HttpPost(DefaultServer.getDefaultServerURL() + prefix + "/bar");
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("POST bar", HttpClientUtils.readResponse(result));
                return null;
            });

            HttpPut put = new HttpPut(DefaultServer.getDefaultServerURL() + prefix + "/bar");
            client.execute(put, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("PUT bar", HttpClientUtils.readResponse(result));
                return null;
            });

            delete = new HttpDelete(DefaultServer.getDefaultServerURL() + prefix + "/bar");
            client.execute(delete, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("DELETE bar", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix);
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("GET /", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + prefix + "/scoop/scoop");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("SCOOP GET", HttpClientUtils.readResponse(result));
                return null;
            });

            post = new HttpPost(DefaultServer.getDefaultServerURL() + prefix + "/scoop/scoop");
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("SCOOP POST", HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }

    @Test
    public void testWildCardRoutingTemplateHandler() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/wild/test/card");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("wild:[test]:[card]", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/wilder/test/card");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("wilder:[test/card]", HttpClientUtils.readResponse(result));
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/wildestBeast");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("wildest:[Beast]", HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }
}
