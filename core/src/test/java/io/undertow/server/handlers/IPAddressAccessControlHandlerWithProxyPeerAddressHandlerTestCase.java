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

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.IPv6Ignore;
import io.undertow.testutils.IPv6Only;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(DefaultServer.class)
public class IPAddressAccessControlHandlerWithProxyPeerAddressHandlerTestCase {

    @BeforeClass
    public static void setup() {
        HttpHandler rootHandler = exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send(exchange.getSourceAddress().getHostString());
            // System.out.println("X-Forwarded-For header = " + exchange.getRequestHeaders().getFirst(Headers.X_FORWARDED_FOR));
            // System.out.println("source address = " + exchange.getSourceAddress());
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
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.contains("127.0.0.1"));
                return null;
            });
        }
    }

    @Test
    @IPv6Only
    public void testWithoutXForwardedForIPv6() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.contains("0:0:0:0:0:0:0:1"));
                return null;
            });
        }
    }

    @Test
    @IPv6Ignore
    public void testWithXForwardedFor1() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            get.addHeader(Headers.X_FORWARDED_FOR_STRING, "127.0.0.2");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.contains("127.0.0.2"));
                return null;
            });
        }
    }

    @Test
    @IPv6Ignore
    public void testWithXForwardedFor2() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            get.addHeader(Headers.X_FORWARDED_FOR_STRING, "127.0.0.1, 192.168.0.10");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.contains("127.0.0.1"));
                return null;
            });
        }
    }

    @Test
    @IPv6Ignore
    public void testWithXForwardedFor3() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            get.addHeader(Headers.X_FORWARDED_FOR_STRING, "127.0.0.1, 127.0.0.2, 192.168.0.10");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.contains("127.0.0.1"));
                return null;
            });
        }
    }

    @Test
    @IPv6Ignore
    public void testForbiddenWithXForwardedFor1() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            get.addHeader(Headers.X_FORWARDED_FOR_STRING, "192.168.0.10");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.FORBIDDEN, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.isEmpty());
                return null;
            });
        }
    }

    @Test
    @IPv6Ignore
    public void testForbiddenWithXForwardedFor2() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            get.addHeader(Headers.X_FORWARDED_FOR_STRING, "192.168.0.10, 192.168.0.20");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.FORBIDDEN, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.isEmpty());
                return null;
            });
        }
    }
}
