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

import io.undertow.UndertowLogger;
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Iterator;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@AjpIgnore(apacheOnly = true)
public class LotsOfHeadersResponseTestCase {

    private static final String HEADER = "HEADER";
    private static final String MESSAGE = "Hello Header";
    private static final int COUNT = 10000;

    @BeforeClass
    public static void setup() {
        // TODO replace by new UndertowOptions.HTTP2_MAX_HEADER_SIZE
        // skip this test if we are running in a scenario with default max header size property
        Assume.assumeNotNull(System.getProperty(Http2Channel.HTTP2_MAX_HEADER_SIZE_PROPERTY));
        final BlockingHandler blockingHandler = new BlockingHandler();
        DefaultServer.setRootHandler(blockingHandler);
        blockingHandler.setRootHandler(exchange -> {
            for (int i = 0; i < COUNT; ++i) {
                exchange.getResponseHeaders().put(HttpString.tryFromString(HEADER + i), MESSAGE + i);
            }
            for (Iterator<HeaderValues> it = exchange.getResponseHeaders().iterator(); it.hasNext(); ) {
                HeaderValues headerValues = it.next();
                UndertowLogger.ROOT_LOGGER.info("HEADER AT SERVER SIDE: " + headerValues.getHeaderName() + ": " + headerValues.getFirst());
            }
        });
    }

    @Test
    public void testLotsOfHeadersInResponse() throws IOException {
        Assert.assertTrue(System.getProperty("os.name").startsWith("Windows"));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            for (int i = 0; i < COUNT; ++i) {
                Header[] header = result.getHeaders(HEADER + i);
                if (header.length == 0) {
                    UndertowLogger.ROOT_LOGGER.info("HEADERS AT CLIENT SIDE, THERE IS A TOTAL OF HEADERS: " + result.getAllHeaders().length);
                    for (Header listedHeader: result.getAllHeaders()) {

                        UndertowLogger.ROOT_LOGGER.info("HEADER: " + listedHeader.getName() + ": " + listedHeader.getValue());
                    }
                    if (result.getAllHeaders().length == 0) {
                        UndertowLogger.ROOT_LOGGER.info("No header found");
                    }
                    Assert.fail("Header " + HEADER + i + " not found");
                }
                Assert.assertEquals(MESSAGE + i, header[0].getValue());
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
