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

import java.io.IOException;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.NetworkUtils;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

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
        TestHttpClient client = new TestHttpClient();
        try {
            final NameVirtualHostHandler handler = new NameVirtualHostHandler()
                    .addHost(NetworkUtils.formatPossibleIpv6Address(DefaultServer.getHostAddress("default")), new SetHeaderHandler(ResponseCodeHandler.HANDLE_200, "myHost", "localhost"))
                    .setDefaultHandler(new SetHeaderHandler(ResponseCodeHandler.HANDLE_200, "myHost", "default"));


            DefaultServer.setRootHandler(handler);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            HttpResponse result = client.execute(get);
            //no origin header, we dny by default
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders("myHost");
            Assert.assertEquals("localhost", header[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("Host", "otherHost");
            result = client.execute(get);
            //no origin header, we dny by default
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            header = result.getHeaders("myHost");
            Assert.assertEquals("default", header[0].getValue());
            HttpClientUtils.readResponse(result);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
