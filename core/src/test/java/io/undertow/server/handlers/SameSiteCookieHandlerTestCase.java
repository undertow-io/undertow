/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(DefaultServer.class)
public class SameSiteCookieHandlerTestCase {

    @Test
    public void testStrict() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "Strict", "foo"));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar; SameSite=Strict", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testLax() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "Lax", "foo"));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar; SameSite=Lax", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNone() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "None", "foo"));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar; Secure; SameSite=None", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testInvalidMode() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "invalidmode", "foo"));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar", header.getValue()); // invalid mode is ignored
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testRegexPattern() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "Lax", "fo.*"));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar; SameSite=Lax", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }


    @Test
    public void testCaseInsensitivePattern() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "Lax", "FOO", false));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar; SameSite=Lax", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testPatternUnmatched() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "Lax", "FO.*"));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testAllCookies() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange -> {
            exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            exchange.setResponseCookie(new CookieImpl("baz", "qux"));
            exchange.setResponseCookie(new CookieImpl("test", "test"));
        }, "Strict"));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] headerArray = result.getHeaders("set-cookie");
                for (Header h : headerArray) {
                    if (h.getValue().contains("foo")) {
                        Assert.assertEquals("foo=bar; SameSite=Strict", h.getValue());
                    }
                    if (h.getValue().contains("baz")) {
                        Assert.assertEquals("baz=qux; SameSite=Strict", h.getValue());
                    }
                    if (h.getValue().contains("test")) {
                        Assert.assertEquals("test=test; SameSite=Strict", h.getValue());
                    }
                }
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }


    @Test
    public void testMultipleCookiesMatched() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange -> {
            exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            exchange.setResponseCookie(new CookieImpl("baz", "qux"));
            exchange.setResponseCookie(new CookieImpl("test", "test"));
        }, "Lax", "foo|baz"));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] headerArray = result.getHeaders("set-cookie");
                for (Header h : headerArray) {
                    if (h.getValue().contains("foo")) {
                        Assert.assertEquals("foo=bar; SameSite=Lax", h.getValue());
                    }
                    if (h.getValue().contains("baz")) {
                        Assert.assertEquals("baz=qux; SameSite=Lax", h.getValue());
                    }
                    if (h.getValue().contains("test")) {
                        Assert.assertEquals("test=test", h.getValue());
                    }
                }
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNoneIncompatibleUA() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "None", "foo"));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            // Chrome version whic is known to be incompatible with the `SameSite=None` attribute
            get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.62 Safari/537.36");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNoneUACheckerDisabled() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "None", "foo", true, false, true));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            // Chrome version whic is known to be incompatible with the `SameSite=None` attribute
            get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.62 Safari/537.36");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar; Secure; SameSite=None", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNoneUACheckerEnabledAlthoughUAHeaderEmpty() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "None", "foo"));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            // Use empty User-Agent header
            get.setHeader(Headers.USER_AGENT.toString(), "");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar; Secure; SameSite=None", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNoneUACheckerEnabledAlthoughUAHeaderNotSet() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "None", "foo"));
        DefaultServer.startSSLServer();

        ClientTlsStrategyBuilder clientTlsStrategyBuilder = ClientTlsStrategyBuilder.create().setSslContext(DefaultServer.getClientSSLContext());

        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(clientTlsStrategyBuilder.buildClassic())
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(30))
                        .build())
                .build();

        try (CloseableHttpClient client = TestHttpClient.custom()
                .setConnectionManager(connectionManager)
                .setUserAgent("").build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            // Don't set any User-Agent header
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar; Secure; SameSite=None", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNoneWithoutSecure() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(exchange ->
                exchange.setResponseCookie(new CookieImpl("foo", "bar")), "None", "foo", true, true, false));
        DefaultServer.startSSLServer();

        try (CloseableHttpClient client = TestHttpClient.withSSLContext(DefaultServer.getClientSSLContext()).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header header = result.getFirstHeader("set-cookie");
                Assert.assertEquals("foo=bar; SameSite=None", header.getValue());
                return FileUtils.readFile(result.getEntity().getContent());
            });
        } finally {
            DefaultServer.stopSSLServer();
        }
    }
}
