/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package io.undertow.server.handlers.blocking;

import io.undertow.server.handlers.BlockingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Carter Kozak
 */
@RunWith(DefaultServer.class)
public class BlockingServerStreamClosureTestCase {

    @Test
    public void testFlushAfterContentLengthReached() throws IOException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        DefaultServer.setRootHandler(new BlockingHandler(exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "1");
            try {
                OutputStream out = exchange.getOutputStream();
                out.write(65);
                out.flush();
                out.close();
            } catch (Throwable t) {
                thrown.set(t);
                throw t;
            }
        }));
        makeSuccessfulRequest("A");
        Throwable maybeFailure = thrown.get();
        if (maybeFailure != null) {
            throw new AssertionError("Unexpected failure", maybeFailure);
        }
    }

    @Test
    public void testEmptyWriteAfterContentLength() throws IOException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        DefaultServer.setRootHandler(new BlockingHandler(exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "1");
            try {
                OutputStream out = exchange.getOutputStream();
                out.write(65);
                out.write(new byte[10], 2, 0);
                out.close();
            } catch (Throwable t) {
                thrown.set(t);
                throw t;
            }
        }));
        makeSuccessfulRequest("A");
        Throwable maybeFailure = thrown.get();
        if (maybeFailure != null) {
            throw new AssertionError("Unexpected failure", maybeFailure);
        }
    }

    @Test
    public void testFlushAfterClose() throws IOException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        DefaultServer.setRootHandler(new BlockingHandler(exchange -> {
            try {
                OutputStream out = exchange.getOutputStream();
                out.write(65);
                out.close();
                out.flush();
            } catch (Throwable t) {
                thrown.set(t);
                throw t;
            }
        }));
        makeSuccessfulRequest("A");
        Throwable maybeFailure = thrown.get();
        if (maybeFailure != null) {
            throw new AssertionError("Unexpected failure", maybeFailure);
        }
    }

    private static void makeSuccessfulRequest(String expectedContent) throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(expectedContent, HttpClientUtils.readResponse(result));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
