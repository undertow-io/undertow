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

import java.io.IOException;

import io.undertow.util.Headers;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.junit.Assert;
import org.junit.Test;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FileUtils;
import io.undertow.util.StatusCodes;
import org.junit.runner.RunWith;

@RunWith(DefaultServer.class)
public class SameSiteCookieHandlerTestCase {

    @Test
    public void testStrict() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "Strict", "foo"));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar; SameSite=Strict", header.getValue());
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testLax() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "Lax", "foo"));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar; SameSite=Lax", header.getValue());
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNone() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "None", "foo"));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar; secure; SameSite=None", header.getValue());
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testInvalidMode() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "invalidmode", "foo"));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar", header.getValue()); // invalid mode is ignored
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testRegexPattern() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "Lax", "fo.*"));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar; SameSite=Lax", header.getValue());
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }


    @Test
    public void testCaseInsensitivePattern() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "Lax", "FOO", false));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar; SameSite=Lax", header.getValue());
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testPatternUnmatched() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "Lax", "FO.*"));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar", header.getValue());
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testAllCookies() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
                exchange.setResponseCookie(new CookieImpl("baz", "qux"));
                exchange.setResponseCookie(new CookieImpl("test", "test"));
            }
        }, "Strict"));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
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
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }


    @Test
    public void testMultipleCookiesMatched() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
                exchange.setResponseCookie(new CookieImpl("baz", "qux"));
                exchange.setResponseCookie(new CookieImpl("test", "test"));
            }
        }, "Lax", "foo|baz"));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
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
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNoneIncompatibleUA() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "None", "foo"));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            // Chrome version whic is known to be incompatible with the `SameSite=None` attribute
            get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.62 Safari/537.36");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar", header.getValue());
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNoneUACheckerDisabled() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "None", "foo", true, false, true));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            // Chrome version whic is known to be incompatible with the `SameSite=None` attribute
            get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.62 Safari/537.36");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar; secure; SameSite=None", header.getValue());
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNoneUACheckerEnabledAlthoughUAHeaderEmpty() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "None", "foo"));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            // Use empty User-Agent header
            get.setHeader(Headers.USER_AGENT.toString(), "");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar; secure; SameSite=None", header.getValue());
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNoneUACheckerEnabledAlthoughUAHeaderNotSet() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "None", "foo"));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient() {
            // Here we need to get client instance that does not set ANY User-Agent header by default.
            @Override
            protected HttpParams createHttpParams() {
                HttpParams params = super.createHttpParams();
                params.removeParameter(CoreProtocolPNames.USER_AGENT);
                HttpConnectionParams.setSoTimeout(params, 30000);
                return params;
            }
        };
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            // Don't set any User-Agent header
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar; secure; SameSite=None", header.getValue());
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void testNoneWithoutSecure() throws IOException {
        DefaultServer.setRootHandler(new SameSiteCookieHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.setResponseCookie(new CookieImpl("foo", "bar"));
            }
        }, "None", "foo", true, true, false));
        DefaultServer.startSSLServer();

        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar; SameSite=None", header.getValue());
            FileUtils.readFile(result.getEntity().getContent());
        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

}
