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

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class HttpServerExchangeTestCase {

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send(exchange.getHostName()
                        + ":" + exchange.getProtocol()
                        + ":" + exchange.getRequestMethod()
                        + ":" + exchange.getHostPort()
                        + ":" + exchange.getRequestURI()
                        + ":" + exchange.getRelativePath()
                        + ":" + exchange.getQueryString());
            }
        });
    }


    @Test
    public void testHttpServerExchange() throws IOException {
        String port = DefaultServer.isAjp() && !DefaultServer.isProxy() ? "9080" : "7777";
        String protocol = DefaultServer.isH2() ? Protocols.HTTP_2_0_STRING : Protocols.HTTP_1_1_STRING;
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/somepath");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals(DefaultServer.getHostAddress() + ":" + protocol + ":GET:" + port + ":/somepath:/somepath:", HttpClientUtils.readResponse(result));
                return null;
            });
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/somepath?a=b");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals(DefaultServer.getHostAddress() + ":" + protocol + ":GET:" + port + ":/somepath:/somepath:a=b", HttpClientUtils.readResponse(result));
                return null;
            });
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/somepath?a=b");
            get.addHeader("Host", "[::1]:8080");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("::1:" + protocol + ":GET:8080:/somepath:/somepath:a=b", HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }
}
