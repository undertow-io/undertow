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

import java.io.IOException;

import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that the correct encoding is selected
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class EncodingSelectionTestCase {

    private static final String HEADER = Headers.CONTENT_ENCODING_STRING;

    /**
     * Tests encoding selection with no qvalue
     * <p>
     * Also tests a lot of non standard formats for Accept-Encoding to make sure that
     * we are liberal in what we accept
     *
     * @throws IOException
     */
    @Test
    public void testBasicEncodingSelect() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            final EncodingHandler handler = new EncodingHandler(new ContentEncodingRepository()
            .addEncodingHandler("compress", ContentEncodingProvider.IDENTITY, 50)
            .addEncodingHandler("bzip", ContentEncodingProvider.IDENTITY, 100))
            .setNext(new HttpHandler() {
                @Override
                public void handleRequest(final HttpServerExchange exchange) throws Exception {
                    exchange.getResponseSender().send("hi"); //we need some content to encode
                }
            });
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(HEADER);
            Assert.assertEquals(0, header.length);
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip compress identity someOtherEncoding");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, " compress, identity, someOtherEncoding,  bzip  , ");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "boo; compress, identity; someOtherEncoding,   , ");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("compress", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "boo; compress; identity; someOtherEncoding,   , ");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("compress", header[0].getValue());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    /**
     * Tests encoding selection with a qvalue
     *
     * @throws IOException
     */
    @Test
    public void testEncodingSelectWithQValue() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            final EncodingHandler handler = new EncodingHandler(new ContentEncodingRepository()
            .addEncodingHandler("compress", ContentEncodingProvider.IDENTITY, 100)
            .addEncodingHandler("bzip", ContentEncodingProvider.IDENTITY, 50))
            .setNext(new HttpHandler() {
                @Override
                public void handleRequest(final HttpServerExchange exchange) throws Exception {
                    exchange.getResponseSender().send("hi"); //we need some content to encode
                }
            });
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip, compress;q=0.6");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_ACCEPTABLE, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00 bzip");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00 bzip;q=0.3");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "compress;q=0.1 bzip;q=0.05");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("compress", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "compress;q=0.1, bzip;q=1.000");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testEncodingSelectionWithQValueAndPredicate() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            final EncodingHandler handler = new EncodingHandler(new ContentEncodingRepository()
            .addEncodingHandler("compress", ContentEncodingProvider.IDENTITY, 100, Predicates.falsePredicate())
            .addEncodingHandler("bzip", ContentEncodingProvider.IDENTITY, 50))
            .setNext(new HttpHandler() {
                @Override
                public void handleRequest(final HttpServerExchange exchange) throws Exception {
                    exchange.getResponseSender().send("hi"); //we need some content to encode
                }
            });
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip, compress;q=0.6");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_ACCEPTABLE, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "compress");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals(0, header.length);
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00 bzip;q=0.3");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "compress;q=0.1 bzip;q=0.05");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "compress;q=0.1, bzip;q=1.000");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
