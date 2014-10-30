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

import java.io.IOException;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import io.undertow.testutils.TestHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that the Origin header is correctly interpreted
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class OriginTestCase {

    private static final String HEADER = "selected";
    private static final String MESSAGE = "My HTTP Request!";

    /**
     * Tests the Origin header is respected when the strictest options are selected
     *
     */
    @Test
    public void testStrictOrigin() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            final OriginHandler handler = new OriginHandler();
            handler.addAllowedOrigins("http://www.mysite.com:80", "http://mysite.com:80");
            DefaultServer.setRootHandler(handler);
            handler.setNext(ResponseCodeHandler.HANDLE_200);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            //no origin header, we dny by default
            Assert.assertEquals(StatusCodes.FORBIDDEN, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ORIGIN_STRING, "http://www.mysite.com:80");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ORIGIN_STRING, "http://www.mysite.com:80");
            get.setHeader(Headers.ORIGIN_STRING, "http://mysite.com:80");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ORIGIN_STRING, "http://www.mysite.com:80");
            get.setHeader(Headers.ORIGIN_STRING, "bogus");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.FORBIDDEN, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.setHeader(Headers.ORIGIN_STRING, "http://www.mysite.com:80");
            get.setHeader(Headers.ORIGIN_STRING, "bogus");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.FORBIDDEN, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
