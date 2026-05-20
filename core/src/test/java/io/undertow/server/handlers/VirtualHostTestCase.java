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
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.NetworkUtils;
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
public class VirtualHostTestCase {

    /**
     * Tests the Origin header is respected when the strictest options are selected
     */
    @Test
    public void testVirtualHost() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final NameVirtualHostHandler handler = new NameVirtualHostHandler()
                    .addHost(NetworkUtils.formatPossibleIpv6Address(DefaultServer.getHostAddress("default")), new SetHeaderHandler(ResponseCodeHandler.HANDLE_200, "myHost", "localhost"))
                    .setDefaultHandler(new SetHeaderHandler(ResponseCodeHandler.HANDLE_200, "myHost", "default"));


            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            client.execute(get, result -> {
                //no origin header, we dny by default
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders("myHost");
                Assert.assertEquals("localhost", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("Host", "otherHost");
            client.execute(get, result -> {
                //no origin header, we dny by default
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders("myHost");
                Assert.assertEquals("default", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });
        }
    }
}
