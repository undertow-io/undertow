/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.params.ConnRoutePNames;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;

@RunWith(DefaultServer.class)
@ProxyIgnore
public class HostHandlerTestCase {

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(Handlers.hostHeaderHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send("OK");
            }
        }));
    }

    @Test
    @Ignore // ignore, since client will add one if there no present
    public void testNoHostHeader() throws Exception {
        test(new String[] {}, null, 400, null);
    }

    @Test
    public void testTooManyHostHeader() throws Exception {
        test(new String[] { "212.138.1.1", "data.com" }, null, 400, null);
    }

    @Test
    public void testIPv4HostHeader() throws Exception {
        // dont test bad IPv4, as this will be valid... reg-name
        test(new String[] { "212.138.1.1" }, null, 200, null);
    }

    @Test
    public void testIPv4AndPortHostHeader() throws Exception {
        test(new String[] { "212.138.1.1:80" }, null, 200, null);
    }

    @Test
    public void testIPv6HostHeader() throws Exception {
        test(new String[] { "[1:2:3:4::]" }, null, 200, null);
    }

    @Test
    public void testIPv6AndPortHostHeader() throws Exception {
        test(new String[] { "[1:2:3:4::]:80" }, null, 200, null);
    }

    @Test
    public void testIPv6HostHeader2() throws Exception {
        test(new String[] { "[1:2:3:4:::]" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6AndPortHostHeader2() throws Exception {
        test(new String[] { "[1:2:3:4::]:80000" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_PORT);
    }

    @Test
    public void testIPv6HostHeader3() throws Exception {
        test(new String[] { "[1:2:3:4::" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6AndPortHostHeader3() throws Exception {
        test(new String[] { "[1:2:3:4:::80" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6HostHeader4() throws Exception {
        test(new String[] { "1:2:3:4::]" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6HostHeader5() throws Exception {
        test(new String[] { "1:2:3:4::" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_PORT);
    }

    @Test
    public void testIPv6HostHeader6() throws Exception {
        //this will just fall into reg-name
        test(new String[] { "1:2:3:4:5:6:7:8" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
    }

    @Test
    public void testIPv6AndPortHostHeader4() throws Exception {
        test(new String[] { "1:2:3:4::]:80" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6AndPortHostHeader6() throws Exception {
        test(new String[] { "1:2:3:4:5:6:7:8]:80" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6EmbeddedIPv4HostHeader() throws Exception {
        test(new String[] { "[1:2:3:4::192.168.32.1]" }, null, 200, null);
    }

    @Test
    public void testIPv6EmbeddedIPv4HostHeader2() throws Exception {
        test(new String[] { "[1:2:3:4::192.168.32.1]:80" }, null, 200, null);
    }

    @Test
    public void testIPv6EmbeddedIPv4HostHeader3() throws Exception {
        test(new String[] { "[1:2:3:4::192.355.32.1]:80" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPvFutureHostHeader() throws Exception {
        test(new String[] { "[vAF.1:2:3:4::]" }, null, 200, null);
    }

    @Test
    public void testIPvFutureHostHeader2() throws Exception {
        test(new String[] { "[vG.1:2:3:4::]" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
    }

    @Test
    public void testIPvFutureHostHeader3() throws Exception {
        test(new String[] { "[vAF.abdc:abcd_-~AF]" }, null, 200, null);
    }

    @Test
    public void testIPvFutureHostHeader4() throws Exception {
        test(new String[] { "[vAF.ImVal1.com-._~!$&'()*+,;=:]" }, null, 200, null);
    }

    @Test
    public void testIPvFutureHostHeader5() throws Exception {
        test(new String[] { "[vAF.]" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPvFutureHostHeader6() throws Exception {
        test(new String[] { "[vAF]" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPvFutureHostHeader7() throws Exception {
        test(new String[] { "[v.abcd]" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPRegNameHostHeader() throws Exception {
        test(new String[] { "366.66.12.12" }, null, 200, null);
    }

    @Test
    public void testIPRegNameHostHeader2() throws Exception {
        test(new String[] { "domain.com%20" }, null, 200, null);
    }

    @Test
    public void testIPRegNameHostHeader3() throws Exception {
        test(new String[] { "domain.com%2" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPRegNameHostHeader4() throws Exception {
        test(new String[] { "doma&n.com%20" }, null, 200, null);
    }

    @Test
    public void testIPRegNameHostHeader5() throws Exception {
        test(new String[] { "ImVal1.com-._~!$&'()*+,;=" }, null, 200, null);
    }

    @Test
    public void testIPRegNameHostHeader6() throws Exception {
        // test userinfo presence?
        test(new String[] { "juicyUserInfo@ImVal1.com-._~!$&'()*+,;=" }, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
    }

    @Test
    public void testAbsoluteURLBad() throws Exception {
        // test userinfo presence?
        test(new String[] { "wrong.com:8080" }, new HttpHost(DefaultServer.getHostAddress(), DefaultServer.getHostPort()), 400, HostHeaderHandler.STATUS_HOST_NO_MATCH);
    }

    @Test
    public void testAbsoluteURLGood() throws Exception {
        // test userinfo presence?
        test(new String[] { DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort() },
                new HttpHost(DefaultServer.getHostAddress(), DefaultServer.getHostPort()), 200, null);
    }

    public void test(final String[] headers, final HttpHost proxy, final int resultCode, final String statusMessage)
            throws ClientProtocolException, IOException {
        TestHttpClient client = new TestHttpClient();
        if (proxy != null) {
            client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            for (String i : headers) {
                get.addHeader(Headers.HOST_STRING, i);
            }
            HttpResponse result = client.execute(get, HttpClientContext.create());
            Assert.assertEquals(result.getStatusLine().getReasonPhrase(), resultCode, result.getStatusLine().getStatusCode());
            if (statusMessage != null) {
                Assert.assertEquals(statusMessage, result.getStatusLine().getReasonPhrase());
            }

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    public void testProxyMode(final String[] headers, final int resultCode, final String statusMessage) throws ClientProtocolException, IOException {
        // this has to be done this way in order to trick apache to use absolute form...
        HttpHost proxy = new HttpHost(DefaultServer.getHostAddress(), DefaultServer.getHostPort());
        TestHttpClient client = new TestHttpClient();
        client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            for (String i : headers) {
                get.addHeader(Headers.HOST_STRING, i);
            }
            HttpResponse result = client.execute(get, HttpClientContext.create());
            Assert.assertEquals(result.getStatusLine().getReasonPhrase(), resultCode, result.getStatusLine().getStatusCode());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
