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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Deque;
import java.util.Map;
import io.undertow.UndertowOptions;
import org.xnio.OptionMap;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@AjpIgnore(apacheOnly = true)
public class LotsOfQueryParametersTestCase {

    private static final String QUERY = "QUERY";
    private static final String MESSAGE = "Hello Query";

    private static final int DEFAULT_MAX_PARAMETERS = 1000;
    private static final int TEST_MAX_PARAMETERS = 10;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                for (Map.Entry<String, Deque<String>> entry : exchange.getQueryParameters().entrySet()) {
                    exchange.getResponseHeaders().put(HttpString.tryFromString(entry.getKey()), entry.getValue().getFirst());
                }
            }
        });
    }

    @Test @AjpIgnore
    public void testLotsOfQueryParameters_Default_Ok() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            StringBuilder qs = new StringBuilder();
            for (int i = 0; i < DEFAULT_MAX_PARAMETERS; ++i) {
                qs.append(QUERY + i);
                qs.append("=");
                qs.append(URLEncoder.encode(MESSAGE + i, "UTF-8"));
                qs.append("&");
            }
            qs.deleteCharAt(qs.length()-1); // delete last useless '&'
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path?" + qs.toString());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            for (int i = 0; i < DEFAULT_MAX_PARAMETERS; ++i) {
                Header[] header = result.getHeaders(QUERY + i);
                Assert.assertEquals(MESSAGE + i, header[0].getValue());
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testLotsOfQueryParameters_Default_BadRequest() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            StringBuilder qs = new StringBuilder();
            // add query parameters more than MAX_PARAMETERS
            for (int i = 0; i < (DEFAULT_MAX_PARAMETERS + 1); ++i) {
                qs.append(QUERY + i);
                qs.append("=");
                qs.append(URLEncoder.encode(MESSAGE + i, "UTF-8"));
                qs.append("&");
            }
            qs.deleteCharAt(qs.length()-1); // delete last useless '&'
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path?" + qs.toString());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test @AjpIgnore
    public void testLotsOfQueryParameters_MaxParameters_Ok() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        TestHttpClient client = new TestHttpClient();
        try {
            StringBuilder qs = new StringBuilder();
            for (int i = 0; i < TEST_MAX_PARAMETERS; ++i) {
                qs.append(QUERY + i);
                qs.append("=");
                qs.append(URLEncoder.encode(MESSAGE + i, "UTF-8"));
                qs.append("&");
            }
            qs.deleteCharAt(qs.length()-1); // delete last useless '&'
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path?" + qs.toString());
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_PARAMETERS, TEST_MAX_PARAMETERS));
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            for (int i = 0; i < TEST_MAX_PARAMETERS; ++i) {
                Header[] header = result.getHeaders(QUERY + i);
                Assert.assertEquals(MESSAGE + i, header[0].getValue());
            }
        } finally {
            DefaultServer.setUndertowOptions(existing);
            client.getConnectionManager().shutdown();
        }
    }

    @Test @AjpIgnore
    public void testLotsOfQueryParameters_MaxParameters_BadRequest() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        TestHttpClient client = new TestHttpClient();
        try {
            StringBuilder qs = new StringBuilder();
            // add query parameters more than specified MAX_PARAMETERS
            for (int i = 0; i < (TEST_MAX_PARAMETERS + 1); ++i) {
                qs.append(QUERY + i);
                qs.append("=");
                qs.append(URLEncoder.encode(MESSAGE + i, "UTF-8"));
                qs.append("&");
            }
            qs.deleteCharAt(qs.length()-1); // delete last useless '&'
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path?" + qs.toString());
            DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.MAX_PARAMETERS, TEST_MAX_PARAMETERS));
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getStatusLine().getStatusCode());
        } finally {
            DefaultServer.setUndertowOptions(existing);
            client.getConnectionManager().shutdown();
        }
    }

}
