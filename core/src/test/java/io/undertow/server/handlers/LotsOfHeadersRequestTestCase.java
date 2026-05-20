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

import io.undertow.UndertowOptions;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@AjpIgnore(apacheOnly = true)
public class LotsOfHeadersRequestTestCase {

    private static final String HEADER = "HEADER";
    private static final String MESSAGE = "Hello Header";

    private static final int DEFAULT_MAX_HEADERS = 200;
    private static final int TEST_MAX_HEADERS = 20;
    private static final int HEADER_OFFSET = 4;
    // Why -4? Because HttpClient adds the following 4 request headers by default:
    //  - Accept-Encoding
    //  - Host
    //  - User-Agent
    //  - Connection: Keep-Alive
    //  - The proxy handler also adds 5 X-forwarded-* headers

    private static int getDefaultMaxHeaders() {
        int res = DEFAULT_MAX_HEADERS - HEADER_OFFSET;
        if (DefaultServer.isProxy()) {
            res -= 5;
        }
        if (DefaultServer.isH2()) {
            res -= 3;
        }
        return res;
    }

    private static int getTestMaxHeaders() {
        int res = TEST_MAX_HEADERS - HEADER_OFFSET;
        if (DefaultServer.isProxy()) {
            res -= 5;
        }
        if (DefaultServer.isH2()) {
            res -= 3;
        }
        return res;
    }

    @BeforeClass
    public static void setup() {
        Assume.assumeFalse(DefaultServer.isH2upgrade());
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(exchange -> {
            HeaderMap headers = exchange.getRequestHeaders();
            for (HeaderValues header : headers) {
                for (String val : header) {
                    exchange.getResponseHeaders().put(HttpString.tryFromString(header.getHeaderName().toString()), val);
                }
            }
        });
    }

    @Test
    @AjpIgnore
    public void testLotsOfHeadersInRequest_Default_Ok() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            for (int i = 0; i < getDefaultMaxHeaders(); ++i) {
                get.addHeader(HEADER + i, MESSAGE + i);
            }
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                for (int i = 0; i < getDefaultMaxHeaders(); ++i) {
                    Header[] header = result.getHeaders(HEADER + i);
                    Assert.assertEquals(MESSAGE + i, header[0].getValue());
                }
                return null;
            });
        }
    }

    @Test
    public void testLotsOfHeadersInRequest_Default_BadRequest() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            // add request headers more than MAX_HEADERS
            for (int i = 0; i <= (getDefaultMaxHeaders() + 1); ++i) {
                get.addHeader(HEADER + i, MESSAGE + i);
            }
            client.execute(get, result -> {
                Assert.assertEquals(DefaultServer.isH2() ? StatusCodes.SERVICE_UNAVAILABLE : StatusCodes.BAD_REQUEST, result.getCode()); //this is not great, but the HTTP/2 impl sends a stream error which is translated to a 503. Should not be a big deal in practice
                return null;
            });
        }
    }

    @Test
    @AjpIgnore
    public void testLotsOfHeadersInRequest_MaxHeaders_Ok() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            for (int i = 0; i < getTestMaxHeaders(); ++i) {
                get.addHeader(HEADER + i, MESSAGE + i);
            }
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_HEADERS, TEST_MAX_HEADERS));
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                for (int i = 0; i < getTestMaxHeaders(); ++i) {
                    Header[] header = result.getHeaders(HEADER + i);
                    Assert.assertEquals(MESSAGE + i, header[0].getValue());
                }
                return null;
            });
        } finally {
            DefaultServer.setUndertowOptions(existing);
        }
    }

    @Test
    public void testLotsOfHeadersInRequest_MaxHeaders_BadRequest() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            // add request headers more than MAX_HEADERS
            for (int i = 0; i <= (getTestMaxHeaders() + 1); ++i) {
                get.addHeader(HEADER + i, MESSAGE + i);
            }
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_HEADERS, TEST_MAX_HEADERS));
            client.execute(get, result -> {
                Assert.assertEquals(DefaultServer.isH2() ? StatusCodes.SERVICE_UNAVAILABLE : StatusCodes.BAD_REQUEST, result.getCode());
                return null;
            });
        } finally {
            DefaultServer.setUndertowOptions(existing);
        }
    }
}
