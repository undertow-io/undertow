/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.test.request;

import javax.servlet.ServletException;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import io.undertow.util.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class RequestPathTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(new ServletInfo("request", RequestPathServlet.class)
                .addMapping("/*"));
    }

    @Test
    public void testRequestPathEncoding() throws Exception {
        runtest("/servletContext/somePath", "/somePath\nhttp://localhost:7777/servletContext/somePath\n/servletContext/somePath\n");
        runtest("/servletContext/somePath?foo=bar", "/somePath\nhttp://localhost:7777/servletContext/somePath\n/servletContext/somePath\nfoo=bar");
        runtest("/servletContext/somePath?foo=b+a+r", "/somePath\nhttp://localhost:7777/servletContext/somePath\n/servletContext/somePath\nfoo=b+a+r");
        runtest("/servletContext/some+path?foo=b+a+r", "/some path\nhttp://localhost:7777/servletContext/some+path\n/servletContext/some+path\nfoo=b+a+r");
    }

    private void runtest(String request, String expectedBody) throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + request);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(expectedBody, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
