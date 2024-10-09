/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Default max header config test case.
 *
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
@AjpIgnore
public class DefaultMaxHeaderTestCase {

    private static final String MESSAGE = "HelloUrl";
    private static final int COUNT = 10000;

    @BeforeClass
    public static void setup() {
        // skip this test if we are running in a scenario with max header size property configured
        Assume.assumeTrue(System.getProperty(Http2Channel.HTTP2_MAX_HEADER_SIZE_PROPERTY) == null);
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(exchange -> exchange.getResponseSender().send(exchange.getRelativePath()));
    }

    @Test
    public void testLargeURL() throws IOException {
        final String message = MESSAGE.repeat(COUNT);
        try (TestHttpClient client = new TestHttpClient()) {
            try {
                final HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/" + message);
                assertStatus(client.execute(get).getStatusLine().getStatusCode());
            } finally {
                client.getConnectionManager().shutdown();
            }
        }
    }

    @Test
    public void testLotsOfQueryParameters_MaxParameters_Ok() throws IOException {
        final OptionMap existing = DefaultServer.getUndertowOptions();
        try (TestHttpClient client = new TestHttpClient()) {
            try {
                final StringBuilder qs = new StringBuilder();
                for (int i = 0; i < UndertowOptions.DEFAULT_MAX_PARAMETERS; ++i) {
                    qs.append("QUERY").append(i);
                    qs.append("=");
                    qs.append(URLEncoder.encode("Hello Query" + i, StandardCharsets.UTF_8));
                    qs.append("&");
                }
                qs.deleteCharAt(qs.length() - 1); // delete last useless '&'
                final HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path?" + qs);
                assertStatus(client.execute(get).getStatusLine().getStatusCode());
            } finally {
                DefaultServer.setUndertowOptions(existing);
                client.getConnectionManager().shutdown();
            }
        }
    }

    private void assertStatus(int statusCode) {
        if (DefaultServer.isH2()) {
            if (DefaultServer.isH2upgrade()) {
                Assert.assertTrue(statusCode == StatusCodes.BAD_REQUEST ||
                        // on proxy, it might happen that it gets a ClosedChannelException before actually reading the 400
                        // response in that case, the client will receive a 503
                        (statusCode == StatusCodes.SERVICE_UNAVAILABLE && DefaultServer.isProxy()) ||
                        // most times test is run it is ok, because the header is processed before the upgrade
                        statusCode == StatusCodes.OK);
            } else {
                Assert.assertTrue(statusCode == StatusCodes.BAD_REQUEST ||
                        // on proxy, it might happen that it gets a ClosedChannelException before actually reading the 400
                        // response in that case, the client will receive a 503
                        (statusCode == StatusCodes.SERVICE_UNAVAILABLE && DefaultServer.isProxy()));
            }
        } else {
            Assert.assertEquals(StatusCodes.OK, statusCode);
        }
    }
}
