/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.test.handlers.encoding;

import java.io.IOException;

import io.undertow.predicate.FalsePredicate;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.encoding.ContentEncodingProvider;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.util.TestHttpClient;
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
     * <p/>
     * Also tests a lot of non standard formats for Accept-Encoding to make sure that
     * we are liberal in what we accept
     *
     * @throws IOException
     */
    @Test
    public void testBasicEncodingSelect() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            final EncodingHandler handler = new EncodingHandler();
            handler.addEncodingHandler("compress", ContentEncodingProvider.IDENTITY, 50);
            handler.addEncodingHandler("bzip", ContentEncodingProvider.IDENTITY, 100);
            handler.setNext(ResponseCodeHandler.HANDLE_200);
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(HEADER);
            Assert.assertEquals(0, header.length);
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip compress identity someOtherEndcoding");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, " compress, identity, someOtherEndcoding,  bzip  , ");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "boo; compress, identity; someOtherEndcoding,   , ");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("compress", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "boo; compress; identity; someOtherEndcoding,   , ");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
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
            final EncodingHandler handler = new EncodingHandler();
            handler.addEncodingHandler("compress", ContentEncodingProvider.IDENTITY, 100);
            handler.addEncodingHandler("bzip", ContentEncodingProvider.IDENTITY, 50);
            handler.setNext(ResponseCodeHandler.HANDLE_200);
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip, compress;q=0.6");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00");
            result = client.execute(get);
            Assert.assertEquals(406, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00 bzip");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00 bzip;q=0.3");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "compress;q=0.1 bzip;q=0.05");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("compress", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "compress;q=0.1, bzip;q=1.000");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
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
            final EncodingHandler handler = new EncodingHandler();
            handler.addEncodingHandler("compress", ContentEncodingProvider.IDENTITY, 100, new FalsePredicate<HttpServerExchange>());
            handler.addEncodingHandler("bzip", ContentEncodingProvider.IDENTITY, 50);
            handler.setNext(ResponseCodeHandler.HANDLE_200);
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip, compress;q=0.6");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00");
            result = client.execute(get);
            Assert.assertEquals(406, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "compress");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals(0, header.length);
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00 bzip;q=0.3");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "compress;q=0.1 bzip;q=0.05");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "compress;q=0.1, bzip;q=1.000");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            header = result.getHeaders(HEADER);
            Assert.assertEquals("bzip", header[0].getValue());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
