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
package io.undertow.server.security;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import io.undertow.security.handlers.SinglePortConfidentialityHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FileUtils;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A simple test case to verify a redirect works.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
public class SimpleConfidentialRedirectTestCase {


    private static int redirectPort = -1;

    @BeforeClass
    public static void setup() throws IOException {
        DefaultServer.startSSLServer();

        HttpHandler current = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                exchange.getResponseHeaders().put(HttpString.tryFromString("scheme"), exchange.getRequestScheme());
                exchange.getResponseHeaders().put(HttpString.tryFromString("uri"), exchange.getRequestURI());
                exchange.getResponseHeaders().put(HttpString.tryFromString("queryString"), exchange.getQueryString());
                exchange.getResponseHeaders().put(HttpString.tryFromString("redirectedToPort"), exchange.getHostPort());
                exchange.endExchange();
            }
        };
        redirectPort = DefaultServer.getHostSSLPort("default");
        current = new SinglePortConfidentialityHandler(current, redirectPort);

        DefaultServer.setRootHandler(current);
    }

    @AfterClass
    public static void stop() throws IOException {
        DefaultServer.stopSSLServer();
    }

    @Test
    public void simpleRedirectTestCase() throws IOException, GeneralSecurityException {
        TestHttpClient client = new TestHttpClient();
        // create our own context to force http-request.config
        // notice that, if we just create http context, the config is ovewritten before request is sent
        // if we add the config to the HttpClient instead, it is ignored
        HttpContext httpContext = new BasicHttpContext() {
            private final RequestConfig config = RequestConfig.copy(RequestConfig.DEFAULT).setNormalizeUri(false).build();
            @Override
            public void setAttribute(final String id, final Object obj) {
                if ("http.request-config".equals(id))
                    return;
                super.setAttribute(id, obj);
            }

            @Override
            public Object getAttribute(final String id) {
                if ("http.request-config".equals(id))
                    return config;
                return super.getAttribute(id);
            }
        };
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            sendRequest(client, httpContext,"/foo", null);
            sendRequest(client,  httpContext,"/foo+bar", null);
            sendRequest(client,  httpContext,"/foo+bar;aa", null);
            sendRequest(client,  httpContext,"/foo+bar;aa", "x=y");
            sendRequest(client,  httpContext,"/foo+bar%3Aaa", "x=%3Ablah");
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @ProxyIgnore
    public void testRedirectWithFullURLInPath() throws IOException {
        DefaultServer.isProxy();
        //now we need to test what happens if the client send a full URI
        //see UNDERTOW-874
        try (Socket socket = new Socket(DefaultServer.getHostAddress(), DefaultServer.getHostPort())) {
            socket.getOutputStream().write(("GET " + DefaultServer.getDefaultServerURL() + "/foo HTTP/1.0\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            String result = FileUtils.readFile(socket.getInputStream());
            Assert.assertTrue(result.contains("Location: " + DefaultServer.getDefaultServerSSLAddress() + "/foo"));
        }
    }

    private void sendRequest(final TestHttpClient client, HttpContext httpContext, final String uri, final String queryString) throws IOException {
        String targetURL = DefaultServer.getDefaultServerURL() + uri;
        if (queryString != null) {
            targetURL = targetURL + "?" + queryString;
        }
        final HttpGet get = new HttpGet(targetURL);
        HttpResponse result = client.execute(get, httpContext);
        Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
        Assert.assertEquals("Unexpected scheme in redirected URI", "https", result.getFirstHeader("scheme").getValue());
        Assert.assertEquals("Unexpected port in redirected URI", String.valueOf(redirectPort), result.getFirstHeader("redirectedToPort").getValue());
        Assert.assertEquals("Unexpected path in redirected URI", uri, result.getFirstHeader("uri").getValue());
        if (queryString != null) {
            Assert.assertEquals("Unexpected query string in redirected URI", queryString,
                    result.getFirstHeader("queryString").getValue());
        }
        HttpClientUtils.readResponse(result);
    }

}
