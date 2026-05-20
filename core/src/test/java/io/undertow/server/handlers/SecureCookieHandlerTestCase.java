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

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FileUtils;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SecureCookieHandlerTestCase {


    @Test
    public void testSecureCookieHandler() throws IOException {
        DefaultServer.setRootHandler(new SecureCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar"))));

        DefaultServer.startSSLServer();
        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar; Secure", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar", header.getValue());
                return null;
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testSecureCookieHandlerWithManuallySetCookie() throws IOException {
        DefaultServer.setRootHandler(new SecureCookieHandler(exchange ->
                exchange.getResponseHeaders().put(Headers.SET_COOKIE, "cookie=value")));

        DefaultServer.startSSLServer();
        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("cookie=value; Secure", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }
}
