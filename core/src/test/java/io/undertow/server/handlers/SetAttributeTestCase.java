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
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.rewrite;


/**
 * Tests the redirect handler
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SetAttributeTestCase {

    @Test
    public void testSettingHeader() throws IOException {
        DefaultServer.setRootHandler(Handlers.setAttribute(ResponseCodeHandler.HANDLE_200, "%{o,Foo}", "%U-%{q,p1}", SetAttributeHandler.class.getClassLoader()));

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/a");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Assert.assertEquals("/path/a-", result.getHeaders("foo")[0].getValue());
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/a?p1=someQp");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Assert.assertEquals("/path/a-someQp", result.getHeaders("foo")[0].getValue());
                return null;
            });


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/a?p1=someQp&p1=value2");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Assert.assertEquals("/path/a-[someQp, value2]", result.getHeaders("foo")[0].getValue());
                return null;
            });
        }
    }


    @Test
    public void testRewrite() throws IOException {
        DefaultServer.setRootHandler(
                rewrite("regex['/somePath/(.*)']", "/otherPath/$1", getClass().getClassLoader(), path()
                        .addPrefixPath("/otherPath", new InfoHandler())
                        .addPrefixPath("/relative",
                                rewrite("path-template['/foo/{bar}/{woz}']", "/foo?bar=${bar}&woz=${woz}", getClass().getClassLoader(), new InfoHandler()))
                ));

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/relative/foo/a/b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("URI: /relative/foo relative: /foo QS:bar=a&woz=b bar: a woz: b", response);
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/somePath/foo/a/b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("URI: /otherPath/foo/a/b relative: /foo/a/b QS:", response);
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/somePath/foo?a=b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("URI: /otherPath/foo relative: /foo QS:a=b a: b", response);
                return null;
            });
        }

        DefaultServer.setRootHandler(
                rewrite("regex['/oldPath']", "/newPath?baz=qux", getClass().getClassLoader(),
                        path().addPrefixPath("/", new InfoHandler()))
        );

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/oldPath?foo=bar");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("URI: /newPath relative: /newPath QS:baz=qux baz: qux", response);
                return null;
            });
        }
    }

    @Test
    public void testSetQueryParameter() throws IOException {
        DefaultServer.setRootHandler(
            Handlers.setAttribute(new InfoHandler(), "%{q,baz}", "qux", SetAttributeHandler.class.getClassLoader())
        );

        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/test");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("URI: /test relative: /test QS:baz=qux baz: qux", response);
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/test?foo=bar");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("URI: /test relative: /test QS:baz=qux&foo=bar baz: qux foo: bar", response);
                return null;
            });
        }
    }

    private class InfoHandler implements HttpHandler {
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            final StringBuilder sb = new StringBuilder("URI: " + exchange.getRequestURI()
                    + " relative: " + exchange.getRelativePath()
                    + " QS:" + exchange.getQueryString());
            for (Map.Entry<String, Deque<String>> param : exchange.getQueryParameters().entrySet()) {
                sb.append(" " + param.getKey() + ": " + param.getValue().getFirst());
            }
            exchange.getResponseSender().send(sb.toString());
        }
    }
}
