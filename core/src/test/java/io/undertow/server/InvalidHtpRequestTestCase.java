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

package io.undertow.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
@HttpOneOnly
public class InvalidHtpRequestTestCase {

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(ResponseCodeHandler.HANDLE_200);
    }

    @Test
    public void testInvalidCharacterInMethod() throws IOException {
        final TestHttpClient client = new TestHttpClient();
        try {
            HttpRequestBase method = new HttpRequestBase() {

                @Override
                public String getMethod() {
                    return "GET;POST";
                }

                @Override
                public URI getURI() {
                    try {
                        return new URI(DefaultServer.getDefaultServerURL());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            HttpResponse result = client.execute(method);
            Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testInvalidCharacterInHeader() throws IOException {
        final TestHttpClient client = new TestHttpClient();
        try {
            HttpRequestBase method = new HttpGet(DefaultServer.getDefaultServerURL());
            method.addHeader("fake;header", "value");
            HttpResponse result = client.execute(method);
            Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testMultipleContentLengths() throws IOException {
        final TestHttpClient client = new TestHttpClient();
        try {
            HttpRequestBase method = new HttpGet(DefaultServer.getDefaultServerURL());
            method.addHeader(Headers.CONTENT_LENGTH_STRING, "0");
            method.addHeader(Headers.CONTENT_LENGTH_STRING, "10");
            HttpResponse result = client.execute(method);
            Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
    @Test
    public void testContentLengthAndTransferEncoding() throws IOException {
        final TestHttpClient client = new TestHttpClient();
        try {
            HttpRequestBase method = new HttpGet(DefaultServer.getDefaultServerURL());
            method.addHeader(Headers.CONTENT_LENGTH_STRING, "0");
            method.addHeader(Headers.TRANSFER_ENCODING_STRING, "chunked");
            HttpResponse result = client.execute(method);
            Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testOverflowingContentLength() throws IOException {
        // 2^64 + 5 must not wrap around to a 5 byte body
        String request = "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 18446744073709551621\r\n\r\n"
                + "hello"
                + "GET /smuggled HTTP/1.1\r\nHost: localhost\r\n\r\n";
        try (Socket socket = new Socket(DefaultServer.getHostAddress(), DefaultServer.getHostPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = socket.getInputStream();
            byte[] buf = new byte[1024];
            try {
                int r;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                }
            } catch (SocketTimeoutException ignore) {
            }
            String response = out.toString(StandardCharsets.US_ASCII);
            Assert.assertTrue(response, response.startsWith("HTTP/1.1 400"));
            Assert.assertFalse(response, response.contains("HTTP/1.1 200"));
        }
    }

    @Test
    public void testMultipleTransferEncoding() throws IOException {
        final TestHttpClient client = new TestHttpClient();
        try {
            HttpRequestBase method = new HttpGet(DefaultServer.getDefaultServerURL());
            method.addHeader(Headers.TRANSFER_ENCODING_STRING, "chunked");
            method.addHeader(Headers.TRANSFER_ENCODING_STRING, "gzip, chunked");
            HttpResponse result = client.execute(method);
            Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
