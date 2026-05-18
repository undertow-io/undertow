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

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.HttpHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

/**
 * Verifies that the %U (request URL) access log token returns the full request URI
 * including the context root, not just the relative path after context-root dispatch.
 *
 * This is the behaviour asserted by ConsoleAccessLogTestCase in EAP/WildFly
 * (JBEAP-32058 / WFLY-21478): when a WAR is deployed at /simple-war and the servlet
 * is at /simple, the access log must record /simple-war/simple, not /simple.
 *
 * UNDERTOW-2695 broke this by switching RequestURLAttribute and RequestLineAttribute
 * from getRequestURI() to getRelativePath(). UNDERTOW-2702 reverted that change.
 */
@RunWith(DefaultServer.class)
public class ConsoleAccessLogRequestUrlTestCase {

    private static volatile String loggedRequestUrl;
    private static volatile String loggedRequestLine;

    private final AccessLogReceiver RECEIVER = msg -> {
        // format: "<requestUrl> <requestLine>"
        String[] parts = msg.split(" ", 2);
        loggedRequestUrl = parts[0];
        loggedRequestLine = parts.length > 1 ? parts[1] : "";
    };

    /**
     * Simulate context-root dispatch: the servlet container sets resolvedPath to the
     * context root (/simple-war) and relativePath to the path within the deployment
     * (/simple). The full requestURI remains /simple-war/simple.
     *
     * %U must return /simple-war/simple (getRequestURI), not /simple (getRelativePath).
     * %r must include /simple-war/simple as well.
     */
    @Test
    public void testRequestUrlIncludesContextRoot() throws IOException, InterruptedException {

        HttpHandler contextRootDispatch = exchange -> {
            // Mimic what the servlet container does on deployment dispatch
            exchange.setResolvedPath("/simple-war");
            exchange.setRelativePath("/simple");
            exchange.getResponseSender().send("OK");
        };

        DefaultServer.setRootHandler(
                new AccessLogHandler(contextRootDispatch, RECEIVER, "%U %r",
                        ConsoleAccessLogRequestUrlTestCase.class.getClassLoader()));

        try (TestHttpClient client = new TestHttpClient()){
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/simple-war/simple");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            // %U — RequestURLAttribute — must be the full URI, not getRelativePath()
            Assert.assertEquals(
                    "%%U should return the full request URI including context root",
                    "/simple-war/simple", loggedRequestUrl);

            // %r — RequestLineAttribute — must also contain the full URI
            Assert.assertTrue(
                    "%%r should contain the full request URI including context root, got: " + loggedRequestLine,
                    loggedRequestLine.contains("/simple-war/simple"));
        }
    }
}
