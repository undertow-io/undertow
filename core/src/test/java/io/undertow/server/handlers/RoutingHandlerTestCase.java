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
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Methods;

import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
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
                    .add(Methods.GET, "/baz", new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            exchange.getResponseSender().send("baz");
                        }
                    })
                    .add(Methods.GET, "/baz/{foo}", new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            exchange.getResponseSender().send("baz-path" + exchange.getQueryParameters().get("foo"));
                        }
                    });

        RoutingHandler convienceHandler = Handlers.routing()
                .get("/bar", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("GET bar");
                    }
                })
                .put("/bar", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("PUT bar");
                    }
                })
                .post("/bar", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("POST bar");
                    }
                })
                .delete("/bar", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("DELETE bar");
                    }
                });

        DefaultServer.setRootHandler(Handlers.routing()
                .add(Methods.GET, "/wild/{test}/*", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("wild:" + exchange.getQueryParameters().get("test") + ":" + exchange.getQueryParameters().get("*"));
                    }
                })
                .add(Methods.GET, "/foo", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("foo");
                    }
                })
                .add(Methods.GET, "/foo", Predicates.parse("contains[value=%{i,SomeHeader},search='special'] "), new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("special foo");
                    }
                })
                .add(Methods.POST, "/foo", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("posted foo");
                    }
                })
                .add(Methods.GET, "/foo/{bar}", new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        exchange.getResponseSender().send("foo-path" + exchange.getQueryParameters().get("bar"));
                    }
                })
                .addAll(commonHandler)
                .addAll(convienceHandler));
    }

    @Test
    public void testRoutingTemplateHandler() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("foo", HttpClientUtils.readResponse(result));

            HttpDelete delete = new HttpDelete(DefaultServer.getDefaultServerURL() + "/foo");
            result = client.execute(delete);
            Assert.assertEquals(StatusCodes.METHOD_NOT_ALLOWED, result.getStatusLine().getStatusCode());
            Assert.assertEquals("", HttpClientUtils.readResponse(result));

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/foo");
            result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("posted foo", HttpClientUtils.readResponse(result));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo");
            get.addHeader("SomeHeader", "value");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("foo", HttpClientUtils.readResponse(result));


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo");
            get.addHeader("SomeHeader", "special");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("special foo", HttpClientUtils.readResponse(result));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo/a");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("foo-path[a]", HttpClientUtils.readResponse(result));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/baz");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("baz", HttpClientUtils.readResponse(result));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/baz/a");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("baz-path[a]", HttpClientUtils.readResponse(result));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/bar");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("GET bar", HttpClientUtils.readResponse(result));

            post = new HttpPost(DefaultServer.getDefaultServerURL() + "/bar");
            result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("POST bar", HttpClientUtils.readResponse(result));

            HttpPut put = new HttpPut(DefaultServer.getDefaultServerURL() + "/bar");
            result = client.execute(put);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("PUT bar", HttpClientUtils.readResponse(result));

            delete = new HttpDelete(DefaultServer.getDefaultServerURL() + "/bar");
            result = client.execute(delete);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("DELETE bar", HttpClientUtils.readResponse(result));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testWildCardRoutingTemplateHandler() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/wild/test/card");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("wild:[test]:[card]", HttpClientUtils.readResponse(result));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
