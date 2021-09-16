/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

import java.io.IOException;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.undertow.testutils.IPv6Ignore;
import io.undertow.testutils.IPv6Only;

@RunWith(DefaultServer.class)
public class IPAddressAccessControlHandlerWithProxyPeerAddressHandlerTestCase {

    @BeforeClass
    public static void setup() {
        HttpHandler rootHandler = new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                exchange.getResponseSender().send(exchange.getSourceAddress().getHostString());
                // System.out.println("X-Forwarded-For header = " + exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_FOR));
                // System.out.println("source address = " + exchange.getSourceAddress());
            }
        };
        if (DefaultServer.isIpv6()) {
            rootHandler = Handlers.ipAccessControl(rootHandler, false).addAllow("::1");
        } else {
            rootHandler = Handlers.ipAccessControl(rootHandler, false).addAllow("127.0.0.0/8");
        }
        DefaultServer.setRootHandler(Handlers.proxyPeerAddress(rootHandler));
    }

    @Test
    @IPv6Ignore
    public void testWithoutXForwardedFor() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.contains("127.0.0.1"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @IPv6Only
    public void testWithoutXForwardedForIPv6() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.contains("0:0:0:0:0:0:0:1"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @IPv6Ignore
    public void testWithXForwardedFor1() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            get.addHeader(Headers.X_FORWARDED_FOR_STRING, "127.0.0.2");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.contains("127.0.0.2"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @IPv6Ignore
    public void testWithXForwardedFor2() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            get.addHeader(Headers.X_FORWARDED_FOR_STRING, "127.0.0.1, 192.168.0.10");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.contains("127.0.0.1"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @IPv6Ignore
    public void testWithXForwardedFor3() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            get.addHeader(Headers.X_FORWARDED_FOR_STRING, "127.0.0.1, 127.0.0.2, 192.168.0.10");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.contains("127.0.0.1"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @IPv6Ignore
    public void testForbiddenWithXForwardedFor1() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            get.addHeader(Headers.X_FORWARDED_FOR_STRING, "192.168.0.10");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.FORBIDDEN, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.isEmpty());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    @IPv6Ignore
    public void testForbiddenWithXForwardedFor2() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            get.addHeader(Headers.X_FORWARDED_FOR_STRING, "192.168.0.10, 192.168.0.20");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.FORBIDDEN, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.isEmpty());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
