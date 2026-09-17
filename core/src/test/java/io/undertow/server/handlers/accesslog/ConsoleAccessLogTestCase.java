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

package io.undertow.server.handlers.accesslog;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.HttpHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

@RunWith(DefaultServer.class)
public class ConsoleAccessLogTestCase {

    private static volatile String message;
    private volatile CountDownLatch latch;

    private final AccessLogReceiver RECEIVER = msg -> {
        message = msg;
        latch.countDown();
    };

    /**
     * Simulates what the servlet container does during context-root dispatch:
     * resolvedPath is set to the context root, relativePath to the path within the deployment.
     * requestURI is left untouched so it keeps the full original URI.
     */
    private static HttpHandler dispatchHandler(HttpHandler next) {
        return exchange -> {
            exchange.setResolvedPath("/simple-war");
            exchange.setRelativePath("/simple");
            next.handleRequest(exchange);
        };
    }

    private static HttpHandler jsonHandler() {
        return exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseSender().send("{}");
        };
    }

    /**
     * Mirrors testAllAttributes: verifies that all core access log attributes have
     * the expected values after a context-root dispatch.
     *
     * Key assertion: requestUrl (%U) must be /simple-war/simple, not /simple.
     */
    @Test
    public void testAllAttributes() throws IOException, InterruptedException {
        latch = new CountDownLatch(1);
        // pipe-delimited: requestUrl|resolvedPath|relativePath|method|scheme|status|port|reason|Content-Type
        DefaultServer.setRootHandler(new AccessLogHandler(
                dispatchHandler(jsonHandler()),
                RECEIVER,
                "%U|%{RESOLVED_PATH}|%R|%m|%{SCHEME}|%s|%p|%{RESPONSE_REASON_PHRASE}|%{o,Content-Type}",
                ConsoleAccessLogTestCase.class.getClassLoader()));

        try (TestHttpClient client = new TestHttpClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/simple-war/simple");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        }

        Assert.assertTrue("Access log not written within timeout", latch.await(10, TimeUnit.SECONDS));

        String[] parts = message.split("\\|", -1);
        Assert.assertEquals("/simple-war/simple", parts[0]);                                          // requestUrl
        Assert.assertEquals("/simple-war",         parts[1]);                                          // resolvedPath
        Assert.assertEquals("/simple",             parts[2]);                                          // relativePath
        Assert.assertEquals("GET",                 parts[3]);                                          // requestMethod
        Assert.assertEquals("http",                parts[4]);                                          // requestScheme
        Assert.assertEquals("200",                 parts[5]);                                          // responseCode
        Assert.assertEquals(String.valueOf(DefaultServer.getHostPort("default")), parts[6]);           // localPort
        Assert.assertEquals("OK",                  parts[7]);                                          // responseReasonPhrase
        Assert.assertTrue("Content-Type should start with application/json, got: " + parts[8],
                parts[8].startsWith("application/json"));
    }

    /**
     * Mirrors testOverrides: verifies attribute values when custom format patterns are used,
     * including query string with the leading ? and response headers.
     *
     * Key assertion: request_url (%U) must be /simple-war/simple, not /simple.
     */
    @Test
    public void testOverrides() throws IOException, InterruptedException {
        latch = new CountDownLatch(1);
        // pipe-delimited: requestUrl|method|scheme|status|port|queryString(with?)|Content-Type
        DefaultServer.setRootHandler(new AccessLogHandler(
                dispatchHandler(jsonHandler()),
                RECEIVER,
                "%U|%m|%{SCHEME}|%s|%p|%q|%{o,Content-Type}",
                ConsoleAccessLogTestCase.class.getClassLoader()));

        try (TestHttpClient client = new TestHttpClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/simple-war/simple?testParam=testValue");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        }

        Assert.assertTrue("Access log not written within timeout", latch.await(10, TimeUnit.SECONDS));

        String[] parts = message.split("\\|", -1);
        Assert.assertEquals("/simple-war/simple", parts[0]);                                          // request_url
        Assert.assertEquals("GET",                 parts[1]);                                          // request_method
        Assert.assertEquals("http",                parts[2]);                                          // request_scheme
        Assert.assertEquals("200",                 parts[3]);                                          // http_response_code
        Assert.assertEquals(String.valueOf(DefaultServer.getHostPort("default")), parts[4]);           // port
        Assert.assertEquals("?testParam=testValue", parts[5]);                                         // query_string
        Assert.assertTrue("Content-Type should start with application/json, got: " + parts[6],
                parts[6].startsWith("application/json"));
    }
}
