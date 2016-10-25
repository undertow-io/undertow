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

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
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

/**
 * A simple test case to verify a redirect works.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(DefaultServer.class)
public class SimpleConfidentialRedirectTestCase {


    @BeforeClass
    public static void setup() throws IOException {
        DefaultServer.startSSLServer();

        HttpHandler current = new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                exchange.getResponseHeaders().put(HttpString.tryFromString("scheme"), exchange.getRequestScheme());
                exchange.getResponseHeaders().put(HttpString.tryFromString("uri"), exchange.getRequestURI());
                exchange.endExchange();
            }
        };

        current = new SinglePortConfidentialityHandler(current, DefaultServer.getHostSSLPort("default"));

        DefaultServer.setRootHandler(current);
    }

    @AfterClass
    public static void stop() throws IOException {
        DefaultServer.stopSSLServer();
    }

    @Test
    public void simpleRedirectTestCase() throws IOException, GeneralSecurityException {
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            sendRequest(client, "/foo");
            sendRequest(client, "/foo+bar");
            sendRequest(client, "/foo+bar;aa");


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

    private void sendRequest(TestHttpClient client, String uri) throws IOException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + uri);
        HttpResponse result = client.execute(get);
        Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
        Assert.assertEquals("https", result.getFirstHeader("scheme").getValue());
        Assert.assertEquals(uri, result.getFirstHeader("uri").getValue());
        HttpClientUtils.readResponse(result);
    }

}
