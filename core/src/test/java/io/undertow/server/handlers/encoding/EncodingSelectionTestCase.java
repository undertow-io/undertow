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
import java.nio.ByteBuffer;

import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
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

    private static final byte[] BZIP_HI = {66,90,104,57,49,65,89,38,83,89,29,94,89,-43,0,0,0,-63,0,0,16,0,96,32,0,33,-104,25,-124,97,119,36,83,-123,9,1,-43,-27,-99,80};
    private static final byte[] LZMA_HI = {93,0,0,-128,0,-1,-1,-1,-1,-1,-1,-1,-1,0,52,26,61,80,43,-33,-1,-1,-8,2,0,0};

    private static final String HEADER = Headers.CONTENT_ENCODING_STRING;

    private static final HttpHandler HANDLER = exchange -> {
        if (!exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING_STRING).get(0).startsWith("boo")) {
            exchange.getResponseSender().send(ByteBuffer.wrap(BZIP_HI));
        } else {
            exchange.getResponseSender().send(ByteBuffer.wrap(LZMA_HI));
        }
    };

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
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final EncodingHandler handler = new EncodingHandler(new ContentEncodingRepository()
                    // "compress" is deprecated
                    //.addEncodingHandler("compress", ContentEncodingProvider.IDENTITY, 50)
                    .addEncodingHandler("lzma", ContentEncodingProvider.IDENTITY, 50)
                    .addEncodingHandler("bzip2", ContentEncodingProvider.IDENTITY, 100))
                    .setNext(HANDLER);
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals(0, header.length);
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip2");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("bzip2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip2 lzma identity someOtherEncoding");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("bzip2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, " lzma, identity, someOtherEncoding, bzip2 , ");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("bzip2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "boo; lzma, identity; someOtherEncoding, , ");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("lzma", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "boo; lzma; identity; someOtherEncoding, , ");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("lzma", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });
        }
    }

    /**
     * Tests encoding selection with a qvalue
     *
     * @throws IOException
     */
    @Test
    public void testEncodingSelectWithQValue() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final EncodingHandler handler = new EncodingHandler(new ContentEncodingRepository()
                    // "compress" is deprecated
                    //.addEncodingHandler("compress", ContentEncodingProvider.IDENTITY, 100)
                    .addEncodingHandler("lzma", ContentEncodingProvider.IDENTITY, 100)
                    .addEncodingHandler("bzip2", ContentEncodingProvider.IDENTITY, 50))
                    .setNext(HANDLER);
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip2, lzma;q=0.6");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("bzip2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.NOT_ACCEPTABLE, result.getCode());
                return HttpClientUtils.readResponse(result);
            });


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00 bzip2");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("bzip2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00 bzip2;q=0.3");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("bzip2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "boo lzma;q=0.1 bzip2;q=0.05");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("lzma", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "lzma;q=0.1, bzip2;q=1.000");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("bzip2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });
        }
    }

    @Test
    public void testEncodingSelectionWithQValueAndPredicate() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final EncodingHandler handler = new EncodingHandler(new ContentEncodingRepository()
                    // "compress" is deprecated
                    //.addEncodingHandler("compress", ContentEncodingProvider.IDENTITY, 100, Predicates.falsePredicate())
                    .addEncodingHandler("lzma", ContentEncodingProvider.IDENTITY, 100, Predicates.falsePredicate())
                    .addEncodingHandler("bzip2", ContentEncodingProvider.IDENTITY, 50))
                    .setNext(HANDLER);
            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "bzip2, lzma;q=0.6");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("bzip2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.NOT_ACCEPTABLE, result.getCode());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "lzma");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals(0, header.length);
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "*;q=0.00 bzip2;q=0.3");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("bzip2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "lzma;q=0.1 bzip2;q=0.05");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("bzip2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, "lzma;q=0.1, bzip2;q=1.000");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(HEADER);
                Assert.assertEquals("bzip2", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });
        }
    }
}
