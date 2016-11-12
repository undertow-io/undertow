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

import java.io.IOException;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.HttpString;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.undertow.UndertowOptions;
import org.xnio.OptionMap;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@AjpIgnore(apacheOnly = true)
public class LotsOfHeadersRequestTestCase {

    private static final String HEADER = "HEADER";
    private static final String MESSAGE = "Hello Header";

    private static final int DEFAULT_MAX_HEADERS = 200;
    private static final int TEST_MAX_HEADERS = 10;
    // Why -3? Because HttpClient adds the following 3 request headers by default:
    //  - Host
    //  - User-Agent
    //  - Connection: Keep-Alive
    private static final int DEFAULT_COUNT = DEFAULT_MAX_HEADERS - 3;
    private static final int TEST_COUNT = TEST_MAX_HEADERS - 3;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                HeaderMap headers = exchange.getRequestHeaders();
                for (HeaderValues header : headers) {
                    for (String val : header) {
                        exchange.getResponseHeaders().put(HttpString.tryFromString(header.getHeaderName().toString()), val);
                    }
                }
            }
        });
    }

    @Test
    public void testLotsOfHeadersInRequest_Default_Ok() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            for (int i = 0; i < DEFAULT_COUNT; ++i) {
                get.addHeader(HEADER + i, MESSAGE + i);
            }
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            for (int i = 0; i < DEFAULT_COUNT; ++i) {
                Header[] header = result.getHeaders(HEADER + i);
                Assert.assertEquals(MESSAGE + i, header[0].getValue());
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testLotsOfHeadersInRequest_Default_BadRequest() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            // add request headers more than MAX_HEADERS
            for (int i = 0; i < (DEFAULT_COUNT + 1); ++i) {
                get.addHeader(HEADER + i, MESSAGE + i);
            }
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testLotsOfHeadersInRequest_MaxHeaders_Ok() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            for (int i = 0; i < TEST_COUNT; ++i) {
                get.addHeader(HEADER + i, MESSAGE + i);
            }
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_HEADERS, TEST_MAX_HEADERS));
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            for (int i = 0; i < TEST_COUNT; ++i) {
                Header[] header = result.getHeaders(HEADER + i);
                Assert.assertEquals(MESSAGE + i, header[0].getValue());
            }
        } finally {
            DefaultServer.setUndertowOptions(existing);
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testLotsOfHeadersInRequest_MaxHeaders_BadRequest() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            // add request headers more than MAX_HEADERS
            for (int i = 0; i < (TEST_COUNT + 1); ++i) {
                get.addHeader(HEADER + i, MESSAGE + i);
            }
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_HEADERS, TEST_MAX_HEADERS));
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getStatusLine().getStatusCode());
        } finally {
            DefaultServer.setUndertowOptions(existing);
            client.getConnectionManager().shutdown();
        }
    }

}
