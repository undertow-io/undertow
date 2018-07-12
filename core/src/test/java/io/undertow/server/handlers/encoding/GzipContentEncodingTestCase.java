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

package io.undertow.server.handlers.encoding;

import io.undertow.io.IoCallback;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.DecompressingEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Random;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class GzipContentEncodingTestCase {

    private static volatile String message;

    @BeforeClass
    public static void setup() {
        final EncodingHandler handler = new EncodingHandler(new ContentEncodingRepository()
                .addEncodingHandler("gzip", new GzipEncodingProvider(), 50, Predicates.parse("max-content-size[5]")))
                .setNext(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {
                        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, message.length() + "");
                        exchange.getResponseSender().send(message, IoCallback.END_EXCHANGE);
                    }
                });

        DefaultServer.setRootHandler(handler);
    }

    /**
     * Tests the use of the deflate content encoding
     *
     * @throws java.io.IOException
     */
    @Test
    public void testGzipEncoding() throws IOException {
        runTest("Hello World");
    }


    /**
     * This message should not be compressed as it is too small
     *
     * @throws java.io.IOException
     */
    @Test
    public void testSmallMessagePredicateDoesNotCompress() throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()){
            message = "Hi";
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "gzip");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
            Assert.assertEquals(0, header.length);
            final String body = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Hi", body);
        }
    }


    //UNDERTOW-331
    @Test
    public void testAcceptIdentity() throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()){
            message = "Hi";
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "identity;q=1, *;q=0");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
            Assert.assertEquals(1, header.length);
            Assert.assertEquals("identity", header[0].getValue());
            final String body = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Hi", body);
        }
    }

    @Test
    public void testGZipEncodingLargeResponse() throws IOException {
        final StringBuilder messageBuilder = new StringBuilder(691963);
        for (int i = 0; i < 691963; ++i) {
            messageBuilder.append("*");
        }
        runTest(messageBuilder.toString());
    }

    @Test
    public void testGzipEncodingRandomSizeResponse() throws IOException {
        int seed = new Random().nextInt();
        //System.out.println("Using seed " + seed);
        try {
            final Random random = new Random(seed);
            int size = random.nextInt(691963);
            final StringBuilder messageBuilder = new StringBuilder(size);
            for (int i = 0; i < size; ++i) {
                messageBuilder.append('*' + random.nextInt(10));
            }
            runTest(messageBuilder.toString());
        } catch (Exception e) {
            throw new RuntimeException("Test failed with seed " + seed, e);
        }
    }

    public void runTest(final String theMessage) throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()){
            message = theMessage;
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "gzip");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            assert result.getEntity() instanceof DecompressingEntity; //no other nice way to be sure we get back gzipped content
            final String body = HttpClientUtils.readResponse(result);
            Assert.assertEquals(theMessage, body);
        }
    }
}
