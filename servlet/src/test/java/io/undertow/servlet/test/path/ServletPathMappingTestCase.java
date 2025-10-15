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

import java.io.IOException;

import jakarta.servlet.ServletException;

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

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletPathMappingTestCase {


    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(
                new ServletInfo("/a/*", PathMappingServlet.class)
                        .addMapping("/a/*"),
                new ServletInfo("/aa", PathMappingServlet.class)
                        .addMapping("/aa"),
                new ServletInfo("/aa/*", PathMappingServlet.class)
                        .addMapping("/aa/*"),
                new ServletInfo("/a/b/*", PathMappingServlet.class)
                        .addMapping("/a/b/*"),
                new ServletInfo("/", PathMappingServlet.class)
                        .addMapping("/"),
                new ServletInfo("*.jsp", PathMappingServlet.class)
                        .addMapping("*.jsp"),
                new ServletInfo("contextRoot", PathMappingServlet.class)
                        .addMapping(""),
                new ServletInfo("foo", PathMappingServlet.class)
                        .addMapping("foo.html"));

    }

    @Test
    public void testSimpleHttpServlet() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/aa");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/aa - /aa - null", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/a/c");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/a/* - /a - /c", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/aa/b");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/aa/* - /aa - /b", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/a/b/c/d");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/a/b/* - /a/b - /c/d", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/a/b");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/a/b/* - /a/b - null", response);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/defaultStuff");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/ - /defaultStuff - null", response);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("contextRoot - / - null", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/bob.jsp");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("*.jsp - /bob.jsp - null", response);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/a/bob.jsp");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/a/* - /a - /bob.jsp", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo.html");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("foo - /foo.html - null", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
