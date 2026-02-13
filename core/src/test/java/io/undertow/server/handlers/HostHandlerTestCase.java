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

import io.undertow.Handlers;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(DefaultServer.class)
@ProxyIgnore
public class HostHandlerTestCase {

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(Handlers.hostHeaderHandler(exchange ->
                exchange.getResponseSender().send("OK")));
    }

    @Test
    @Ignore // ignore, since client will add one if there no present
    public void testNoHostHeader() throws Exception {
        test(new String[]{}, null, 400, null);
    }

    @Test
    public void testTooManyHostHeader() throws Exception {
        test(new String[]{"212.138.1.1", "data.com"}, null, 400, null);
    }

    @Test
    public void testIPv4HostHeader() throws Exception {
        // dont test bad IPv4, as this will be valid... reg-name
        test(new String[]{"212.138.1.1"}, null, 200, null);
    }

    @Test
    public void testIPv4AndPortHostHeader() throws Exception {
        test(new String[]{"212.138.1.1:80"}, null, 200, null);
    }

    @Test
    public void testIPv6HostHeader() throws Exception {
        test(new String[]{"[1:2:3:4::]"}, null, 200, null);
    }

    @Test
    public void testIPv6AndPortHostHeader() throws Exception {
        test(new String[]{"[1:2:3:4::]:80"}, null, 200, null);
    }

    @Test
    public void testIPv6HostHeader2() throws Exception {
        test(new String[]{"[1:2:3:4:::]"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6AndPortHostHeader2() throws Exception {
        test(new String[]{"[1:2:3:4::]:80000"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_PORT);
    }

    @Test
    public void testIPv6HostHeader3() throws Exception {
        test(new String[]{"[1:2:3:4::"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6AndPortHostHeader3() throws Exception {
        test(new String[]{"[1:2:3:4:::80"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6HostHeader4() throws Exception {
        test(new String[]{"1:2:3:4::]"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6HostHeader5() throws Exception {
        test(new String[]{"1:2:3:4::"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_PORT);
    }

    @Test
    public void testIPv6HostHeader6() throws Exception {
        //this will just fall into reg-name
        test(new String[]{"1:2:3:4:5:6:7:8"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
    }

    @Test
    public void testIPv6AndPortHostHeader4() throws Exception {
        test(new String[]{"1:2:3:4::]:80"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6AndPortHostHeader6() throws Exception {
        test(new String[]{"1:2:3:4:5:6:7:8]:80"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPv6EmbeddedIPv4HostHeader() throws Exception {
        test(new String[]{"[1:2:3:4::192.168.32.1]"}, null, 200, null);
    }

    @Test
    public void testIPv6EmbeddedIPv4HostHeader2() throws Exception {
        test(new String[]{"[1:2:3:4::192.168.32.1]:80"}, null, 200, null);
    }

    @Test
    public void testIPv6EmbeddedIPv4HostHeader3() throws Exception {
        test(new String[]{"[1:2:3:4::192.355.32.1]:80"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPvFutureHostHeader() throws Exception {
        test(new String[]{"[vAF.1:2:3:4::]"}, null, 200, null);
    }

    @Test
    public void testIPvFutureHostHeader2() throws Exception {
        test(new String[]{"[vG.1:2:3:4::]"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
    }

    @Test
    public void testIPvFutureHostHeader3() throws Exception {
        test(new String[]{"[vAF.abdc:abcd_-~AF]"}, null, 200, null);
    }

    @Test
    public void testIPvFutureHostHeader4() throws Exception {
        test(new String[]{"[vAF.ImVal1.com-._~!$&'()*+,;=:]"}, null, 200, null);
    }

    @Test
    public void testIPvFutureHostHeader5() throws Exception {
        test(new String[]{"[vAF.]"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPvFutureHostHeader6() throws Exception {
        test(new String[]{"[vAF]"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPvFutureHostHeader7() throws Exception {
        test(new String[]{"[v.abcd]"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPRegNameHostHeader() throws Exception {
        test(new String[]{"366.66.12.12"}, null, 200, null);
    }

    @Test
    public void testIPRegNameHostHeader2() throws Exception {
        test(new String[]{"domain.com%20"}, null, 200, null);
    }

    @Test
    public void testIPRegNameHostHeader3() throws Exception {
        test(new String[]{"domain.com%2"}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL);
    }

    @Test
    public void testIPRegNameHostHeader4() throws Exception {
        test(new String[]{"doma&n.com%20"}, null, 200, null);
    }

    @Test
    public void testIPRegNameHostHeader5() throws Exception {
        test(new String[]{"ImVal1.com-._~!$&'()*+,;="}, null, 200, null);
    }

    @Test
    public void testIPRegNameHostHeader6() throws Exception {
        // test userinfo presence?
        test(new String[]{"juicyUserInfo@ImVal1.com-._~!$&'()*+,;="}, null, 400, HostHeaderHandler.STATUS_MALFORMED_IP_LITERAL_BAD_CHARS);
    }

    @Test
    public void testAbsoluteURLBad() throws Exception {
        // test userinfo presence?
        test(new String[]{"wrong.com:8080"}, new HttpHost(DefaultServer.getHostAddress(), DefaultServer.getHostPort()), 400, HostHeaderHandler.STATUS_HOST_NO_MATCH);
    }

    @Test
    public void testAbsoluteURLGood() throws Exception {
        // test userinfo presence?
        test(new String[]{DefaultServer.getHostAddress() + ":" + DefaultServer.getHostPort()},
                new HttpHost(DefaultServer.getHostAddress(), DefaultServer.getHostPort()), 200, null);
    }

    @Test
    public void testEmptyHost() throws Exception {
        // test userinfo presence?
        test(new String[]{""},
                null, 200, null);
    }

    @Test
    public void testEmptyHost2() throws Exception {
        // test userinfo presence?
        test(new String[]{""},
                new HttpHost(DefaultServer.getHostAddress(), DefaultServer.getHostPort()), 400, HostHeaderHandler.STATUS_HOST_NO_MATCH);
    }

    public void test(final String[] headers, final HttpHost proxy, final int resultCode, final String statusMessage)
            throws IOException {
        HttpClientBuilder hcBuilder = TestHttpClient.custom();
        if (proxy != null) {
            RequestConfig rc = RequestConfig.custom().setProxy(proxy).build();
            hcBuilder.setDefaultRequestConfig(rc);
        }
        try (CloseableHttpClient client = hcBuilder.build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            for (String i : headers) {
                get.addHeader(Headers.HOST_STRING, i);
            }
            client.execute(get, HttpClientContext.create(), result -> {
                Assert.assertEquals(result.getReasonPhrase(), resultCode, result.getCode());
                if (statusMessage != null) {
                    Assert.assertEquals(statusMessage, result.getReasonPhrase());
                }
                return null;
            });
        }
    }

    public void testProxyMode(final String[] headers, final int resultCode) throws IOException {
        // this has to be done this way in order to trick apache to use absolute form...
        HttpHost proxy = new HttpHost(DefaultServer.getHostAddress(), DefaultServer.getHostPort());
        RequestConfig rc = RequestConfig.custom().setProxy(proxy).build();
        try (CloseableHttpClient client = TestHttpClient.custom().setDefaultRequestConfig(rc).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL());
            for (String i : headers) {
                get.addHeader(Headers.HOST_STRING, i);
            }
            client.execute(get, HttpClientContext.create(), result -> {
                Assert.assertEquals(result.getReasonPhrase(), resultCode, result.getCode());
                return null;
            });
        }
    }
}
