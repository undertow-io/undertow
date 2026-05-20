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

import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URI;

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
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpUriRequestBase method = new HttpUriRequestBase("GET;POST", URI.create(DefaultServer.getDefaultServerURL()));
            client.execute(method, result -> {
                Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getCode());
                return null;
            });
        }
    }


    @Test
    public void testInvalidCharacterInHeader() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpUriRequestBase method = new HttpGet(DefaultServer.getDefaultServerURL());
            method.addHeader("fake;header", "value");
            client.execute(method, result -> {
                Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getCode());
                return null;
            });
        }
    }

    @Ignore("HttpClient does not allow 'Content-Length' and 'Transfer-Encoding' headers added in advance")
    @Test
    public void testMultipleContentLengths() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpUriRequestBase method = new HttpGet(DefaultServer.getDefaultServerURL());
            method.addHeader(Headers.CONTENT_LENGTH_STRING, "0");
            method.addHeader(Headers.CONTENT_LENGTH_STRING, "10");
            client.execute(method, result -> {
                Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getCode());
                return null;
            });
        }
    }

    @Ignore("HttpClient does not allow 'Content-Length' and 'Transfer-Encoding' headers added in advance")
    @Test
    public void testContentLengthAndTransferEncoding() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpUriRequestBase method = new HttpGet(DefaultServer.getDefaultServerURL());
            method.addHeader(Headers.CONTENT_LENGTH_STRING, "0");
            method.addHeader(Headers.TRANSFER_ENCODING_STRING, "chunked");
            client.execute(method, result -> {
                Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getCode());
                return null;
            });
        }
    }

    @Ignore("HttpClient does not allow 'Content-Length' and 'Transfer-Encoding' headers added in advance")
    @Test
    public void testMultipleTransferEncoding() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpUriRequestBase method = new HttpGet(DefaultServer.getDefaultServerURL());
            method.addHeader(Headers.TRANSFER_ENCODING_STRING, "chunked");
            method.addHeader(Headers.TRANSFER_ENCODING_STRING, "gzip, chunked");
            client.execute(method, result -> {
                Assert.assertEquals(StatusCodes.BAD_REQUEST, result.getCode());
                return null;
            });
        }
    }
}
