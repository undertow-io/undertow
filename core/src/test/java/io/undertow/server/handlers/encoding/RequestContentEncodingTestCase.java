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

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.undertow.conduits.GzipStreamSourceConduit;
import io.undertow.conduits.InflatingStreamSourceConduit;
import io.undertow.io.IoCallback;
import io.undertow.io.Receiver;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

/**
 * This is not part of the HTTP spec
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class RequestContentEncodingTestCase {

    private static volatile String message;

    @BeforeClass
    public static void setup() {
        final ContentEncodingRepository contentEncodingRepository = new ContentEncodingRepository()
                .addEncodingHandler("deflate", new DeflateEncodingProvider(), 50)
                .addEncodingHandler("gzip", new GzipEncodingProvider(), 60);
        final EncodingHandler encode = new EncodingHandler(contentEncodingRepository)
                .setNext(new HttpHandler() {
                    @Override
                    public void handleRequest(final HttpServerExchange exchange) throws Exception {
                        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, message.length() + "");
                        exchange.getResponseSender().send(message, IoCallback.END_EXCHANGE);
                    }
                });
        final EncodingHandler wrappedEncode = new EncodingHandler(contentEncodingRepository).setNext(encode);

        final HttpHandler decode = new RequestEncodingHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getRequestReceiver().receiveFullBytes(new Receiver.FullBytesCallback() {
                    @Override
                    public void handle(HttpServerExchange exchange, byte[] message) {
                        exchange.getResponseSender().send(ByteBuffer.wrap(message));
                    }
                });
            }
        }).addEncoding("deflate", InflatingStreamSourceConduit.WRAPPER)
                .addEncoding("gzip", GzipStreamSourceConduit.WRAPPER);
        final HttpHandler wrappedDecode = new RequestEncodingHandler(decode)
                .addEncoding("deflate", InflatingStreamSourceConduit.WRAPPER)
                .addEncoding("gzip", GzipStreamSourceConduit.WRAPPER);
        PathHandler pathHandler = new PathHandler();
        pathHandler.addPrefixPath("/encode", wrappedEncode);
        pathHandler.addPrefixPath("/decode", wrappedDecode);

        DefaultServer.setRootHandler(pathHandler);
    }

    /**
     * Tests the use of the deflate contentent encoding
     *
     * @throws IOException
     */
    @Test
    public void testDeflateEncoding() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; ++i) {
            sb.append("a message");
        }
        runTest(sb.toString(), "deflate");
        runTest("Hello World", "deflate");

    }

    @Test
    public void testGzipEncoding() throws IOException {
        runTest("Hello World", "gzip");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; ++i) {
            sb.append("a message");
        }
        runTest(sb.toString(), "gzip");
    }


    public void runTest(final String theMessage, String encoding) throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().disableContentCompression().build()){
            message = theMessage;
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/encode");
            get.setHeader(Headers.ACCEPT_ENCODING_STRING, encoding);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(Headers.CONTENT_ENCODING_STRING);
            Assert.assertEquals(encoding, header[0].getValue());
            byte[] body = HttpClientUtils.readRawResponse(result);

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/decode");
            post.setEntity(new ByteArrayEntity(body));
            post.addHeader(Headers.CONTENT_ENCODING_STRING, encoding);

            result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String sb = HttpClientUtils.readResponse(result);
            Assert.assertEquals(theMessage.length(), sb.length());
            Assert.assertEquals(theMessage, sb);

        }
    }
}
