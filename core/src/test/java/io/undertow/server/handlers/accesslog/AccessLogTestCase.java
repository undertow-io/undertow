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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.StoredResponseHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class AccessLogTestCase {

    private static volatile String message;

    private volatile CountDownLatch latch;


    private final AccessLogReceiver RECEIVER = new AccessLogReceiver() {


        @Override
        public void logMessage(final String msg) {
            message = msg;
            latch.countDown();
        }
    };

    private static final HttpHandler HELLO_HANDLER = new HttpHandler() {
        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("HelloResponse");
        }
    };

    @Test
    public void testRemoteAddress() throws IOException, InterruptedException {
        latch = new CountDownLatch(1);
        DefaultServer.setRootHandler(new StoredResponseHandler(new AccessLogHandler(HELLO_HANDLER, RECEIVER, "Remote address %a Code %s test-header %{i,test-header} %{STORED_RESPONSE}", AccessLogFileTestCase.class.getClassLoader())));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("test-header", "test-value");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("HelloResponse", HttpClientUtils.readResponse(result));
            latch.await(10, TimeUnit.SECONDS);
            Assert.assertEquals("Remote address " + DefaultServer.getDefaultServerAddress().getAddress().getHostAddress() + " Code 200 test-header test-value HelloResponse", message);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
