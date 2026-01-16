/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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
import java.util.concurrent.ExecutionException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ratelimit.BitShiftSingleWindowRateLimiter;
import io.undertow.server.handlers.ratelimit.RateLimiter;
import io.undertow.server.handlers.ratelimit.RateLimitingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

@RunWith(DefaultServer.class)
@ProxyIgnore //UNDERTOW-2654
public class RateLimiterTestCase {
    //NOTE: even with limited testing this test takes long....
    private static final int DURATION = 60;
    private static final int REQUEST_LIMIT = 20;
    private RateLimiter<?> limiter;
    private RateLimitingHandler handler;

    @Before
    public void setup() {
        limiter = new BitShiftSingleWindowRateLimiter(DURATION, REQUEST_LIMIT);
        handler = Handlers.rateLimitingHandler(limiter, new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.setStatusCode(200);
                exchange.endExchange();
            }
        });
        handler.setSignalLimits(true);
        DefaultServer.setRootHandler(new BlockingHandler(handler));
    }

    @Test
    public void testWindowSliding() throws ExecutionException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            for (int i = 0; i < 3; i++) {
                for (int requestCounter = 0; requestCounter <= limiter.getRequestLimit(); requestCounter++) {
                        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
                        HttpResponse result = client.execute(get);
                        final int r = extract(RateLimitingHandler.HEADER_NAME_RATE_LIMIT, "r", result);
                        //dont check window as it will be random depending when test runs, only if its there
                        extract(RateLimitingHandler.HEADER_NAME_RATE_LIMIT, "t", result);
                        if (requestCounter == limiter.getRequestLimit() ) {
                            Assert.assertEquals("Iteration: " + i + ", request in sequence: " + requestCounter, handler.getStatucCode(), result.getStatusLine().getStatusCode());
                            Assert.assertEquals("Iteration: " + i + ", request in sequence: " + requestCounter, handler.getStatusMessage(), result.getStatusLine().getReasonPhrase());
                            Assert.assertEquals(limiter.getRequestLimit()-requestCounter, r);
                        } else {
                            Assert.assertEquals("Iteration: " + i + ", request in sequence: " + requestCounter, StatusCodes.OK, result.getStatusLine().getStatusCode());
                            Assert.assertEquals(limiter.getRequestLimit()-requestCounter-1, r);
                        }
                        HttpClientUtils.readResponse(result);
                }
                // do 2x, since window slides "randomly" - from our POV
                Thread.currentThread().sleep(limiter.getWindowDuration()*1000 * 2);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private int extract(final String headerName, final String key, final HttpResponse result) {
        final Header[] headers = result.getHeaders(headerName);
        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.length);
        final Header h = headers[0];
        Assert.assertNotNull(h);
        final String value = h.getValue();
        Assert.assertNotNull(value);
        String[] splits = value.split(";");
        String kvp = null;
        for(String split:splits) {
            if(split.startsWith(key)) {
                kvp=split;
                break;
            }
        }
        Assert.assertNotNull(kvp);
        return Integer.parseInt(kvp.split("=")[1]);
    }

    @Test
    public void testDurationOfSlide() throws ExecutionException, InterruptedException {
        int timeDiff = limiter.getWindowDuration()*1000/(limiter.getRequestLimit()-2);
        TestHttpClient client = new TestHttpClient();
        try {
            for (int requestCount = 0; requestCount * 2 < limiter.getRequestLimit(); requestCount++) {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
                HttpResponse result = client.execute(get);
                    Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                HttpClientUtils.readResponse(result);
                Thread.currentThread().sleep(timeDiff);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
