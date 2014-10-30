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

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@AjpIgnore(apacheOnly = true)
public class LotsOfQueryParametersTestCase {

    private static final String HEADER = "HEADER";
    private static final String MESSAGE = "Hello Header";
    private static final int COUNT = 200;

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

    @Test
    public void testLotsOfQueryParameters() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            StringBuilder qs = new StringBuilder();
            for (int i = 0; i < COUNT; ++i) {
                qs.append(HEADER + i);
                qs.append("=");
                qs.append(URLEncoder.encode(MESSAGE + i, "UTF-8"));
                qs.append("&");
            }
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path?" + qs.toString());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            for (int i = 0; i < COUNT; ++i) {
                Header[] header = result.getHeaders(HEADER + i);
                Assert.assertEquals(MESSAGE + i, header[0].getValue());
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
