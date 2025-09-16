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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

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

        final HttpHandler decode = new RequestEncodingHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getRequestReceiver().receiveFullBytes(new Receiver.FullBytesCallback() {
                    @Override
                    public void handle(HttpServerExchange exchange, byte[] message) {
                        Assert.assertTrue(exchange.getRequestContentLength()>0);
                        exchange.getResponseSender().send(ByteBuffer.wrap(message));
                    }
                });
            }
        }).addEncoding("deflate", InflatingStreamSourceConduit.WRAPPER)
                .addEncoding("gzip", GzipStreamSourceConduit.WRAPPER);

        PathHandler pathHandler = new PathHandler();
        pathHandler.addPrefixPath("/encode", encode);
        pathHandler.addPrefixPath("/decode", decode);

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

        // the default buffer size used for tests is 8kb
        // ensure we send enough data to require multiple buffer allocations when deflating
        Random random = new Random();
        byte[] randomBytes = new byte[65536];
        random.nextBytes(randomBytes);
        Base64.Encoder encoder = Base64.getEncoder();
        String randomString = encoder.encodeToString(randomBytes);
        runTest(randomString, "deflate");
    }

    @Test
    public void testGzipEncoding() throws IOException {
        runTest("Hello World", "gzip");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; ++i) {
            sb.append("a message");
        }
        runTest(sb.toString(), "gzip");

        Random random = new Random();
        byte[] randomBytes = new byte[65536];
        random.nextBytes(randomBytes);
        Base64.Encoder encoder = Base64.getEncoder();
        String randomString = encoder.encodeToString(randomBytes);
        runTest(randomString, "gzip");
    }

    private static final String MESSAGE = "COMPRESSED I'AM";
    private static final byte[] COMPRESSED_MESSAGE = { 0x78, (byte) (0x9C & 0xFF), 0x73, (byte) (0xF6 & 0xFF),
            (byte) (0xF7 & 0xFF), 0x0D, 0x08, 0x72, 0x0D, 0x0E, 0x76, 0x75, 0x51, (byte) (0xF0 & 0xFF), 0x54, 0x77,
            (byte) (0xF4 & 0xFF), 0x05, 0x00, 0x22, 0x35, 0x04, 0x14 };

    @Test
    public void testDeflateWithNoWrapping() throws IOException {
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/decode");
        post.setEntity(new ByteArrayEntity(COMPRESSED_MESSAGE));
        post.addHeader(Headers.CONTENT_ENCODING_STRING, "deflate");

        try (CloseableHttpClient client = HttpClientBuilder.create().disableContentCompression().build()) {
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String sb = HttpClientUtils.readResponse(result);
            Assert.assertEquals(MESSAGE.length(), sb.length());
            Assert.assertEquals(MESSAGE, sb);
        }
    }

    @Test
    public void testDeflateWithNoWrappingLargeRequestBody() throws IOException {
        Random random = new Random();
        byte[] randomBytes = new byte[16384];
        random.nextBytes(randomBytes);
        Base64.Encoder encoder = Base64.getEncoder();
        String randomString = encoder.encodeToString(randomBytes);

        byte[] input = randomString.getBytes(StandardCharsets.UTF_8);
        byte[] output = new byte[65536];
        Deflater deflater = new Deflater(-1, true);
        deflater.setInput(input);
        deflater.finish();
        int len = deflater.deflate(output);
        deflater.end();

        byte[] bodyBytes = Arrays.copyOf(output, len);
        HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/decode");
        post.setEntity(new ByteArrayEntity(bodyBytes));
        post.addHeader(Headers.CONTENT_ENCODING_STRING, "deflate");

        try (CloseableHttpClient client = HttpClientBuilder.create().disableContentCompression().build()) {
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String sb = HttpClientUtils.readResponse(result);
            Assert.assertEquals(randomString.length(), sb.length());
            Assert.assertEquals(randomString, sb);
        }
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
