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

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.builder.PredicatedHandlersParser;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.NetworkUtils;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URISyntaxException;

import static io.undertow.testutils.DefaultServer.getHostAddress;
import static io.undertow.testutils.DefaultServer.getHostPort;

/**
 * @author Luis Mineiro
 */
@RunWith(DefaultServer.class)
public class PredicatedHandlersProxyTestCase {

    private static Undertow server1;
    private static Undertow server2;

    @BeforeClass
    public static void setup() throws URISyntaxException {
        int port = getHostPort("default") + 1;
        final NameVirtualHostHandler handler = new NameVirtualHostHandler()
                .addHost("original-host", new SetHeaderHandler(ResponseCodeHandler.HANDLE_200, "myHost", "original-host"))
                .setDefaultHandler(new SetHeaderHandler(ResponseCodeHandler.HANDLE_200, "myHost", "upstream-host"));

        server1 = Undertow.builder()
                .addHttpListener(port, getHostAddress("default"))
                .setHandler(handler)
                .build();

        server1.start();
    }

    @Test
    public void testProxy() throws Exception {

        TestHttpClient client = new TestHttpClient();

        int port = getHostPort("default");
        String upstreamUrl = "http://" + NetworkUtils.formatPossibleIpv6Address(getHostAddress("default")) + ":" + (port + 1);
        DefaultServer.setRootHandler(
                Handlers.predicates(
                        PredicatedHandlersParser.parse(
                                String.format(
                                        "path-suffix['.html'] -> reverse-proxy[hosts={'%1$s'}, rewrite-host-header=true]\n" +
                                        "path-suffix['.jsp'] -> reverse-proxy[hosts={'%1$s'}]", upstreamUrl
                                ), getClass().getClassLoader()), ResponseCodeHandler.HANDLE_404));

        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo.html");
        get.addHeader("Host", "original-host");
        HttpResponse result = client.execute(get);
        Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
        Header[] header = result.getHeaders("myHost");
        Assert.assertEquals("upstream-host", header[0].getValue());
        HttpClientUtils.readResponse(result);

        get = new HttpGet(DefaultServer.getDefaultServerURL() + "/foo.jsp");
        get.addHeader("Host", "original-host");
        result = client.execute(get);
        Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
        header = result.getHeaders("myHost");
        Assert.assertEquals("original-host", header[0].getValue());
        HttpClientUtils.readResponse(result);

    }

    @AfterClass
    public static void teardown() {
        server1.stop();
        // sleep 1 s to prevent BindException (Address already in use) when running the CI
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {}
    }

}
