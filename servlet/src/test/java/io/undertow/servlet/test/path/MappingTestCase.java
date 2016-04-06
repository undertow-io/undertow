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

package io.undertow.servlet.test.path;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.servlet.ServletException;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class MappingTestCase {
    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(
                new ServletInfo("path", GetMappingServlet.class)
                        .addMapping("/path/*")
                        .addMapping("/exact")
                        .addMapping("*.ext")
                        .addMapping("")
                        .addMapping("/"));

    }

    @Test
    public void testGetMapping() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path/foo");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Mapping match:PATH\n" +
                    "Match value:/foo\n" +
                    "Pattern:/path/*", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo.ext");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Mapping match:EXTENSION\n" +
                    "Match value:/foo\n" +
                    "Pattern:*.ext", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Mapping match:CONTEXT_ROOT\n" +
                    "Match value:\n" +
                    "Pattern:", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/doesnotexist");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Mapping match:DEFAULT\n" +
                    "Match value:/\n" +
                    "Pattern:/", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/exact");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Mapping match:EXACT\n" +
                    "Match value:/exact\n" +
                    "Pattern:/exact", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
