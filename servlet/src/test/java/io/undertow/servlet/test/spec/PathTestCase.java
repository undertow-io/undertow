/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
package io.undertow.servlet.test.spec;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.PathTestServlet;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * <p>Test for the paths in the HttpServletRequest with different URLs.</p>
 *
 * @author rmartinc
 */
@RunWith(DefaultServer.class)
public class PathTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(PathTestCase.class.getClassLoader())
                .setContextPath("/context")
                .setDeploymentName("servletContext.war")
                .addServlet(new ServletInfo("path-servlet", PathTestServlet.class).addMapping("/path/*").addMapping("*.info"));
        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testNoEncoded() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/context/path/path-info?n1=v1&n2=v2");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:/path-info queryString:n1=v1&n2=v2 servletPath:/path requestUri:/context/path/path-info", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testEncodedPath() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/context/path/path%25info?n1=v%251&n2=v%252");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:/path%info queryString:n1=v%251&n2=v%252 servletPath:/path requestUri:/context/path/path%25info", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testEncodedExtension() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/context/other/pa%25th.info?n1=v%251&n2=v%252");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:null queryString:n1=v%251&n2=v%252 servletPath:/other/pa%th.info requestUri:/context/other/pa%25th.info", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testEncodedPathParams() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/context/path/path%25info;p1=v1;p2=v2?n1=v%251&n2=v%252");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:/path%info queryString:n1=v%251&n2=v%252 servletPath:/path requestUri:/context/path/path%25info;p1=v1;p2=v2", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testEncodedExtensionPathParams() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/context/other/pa%25th.info;p1=v1;p2=v2?n1=v%251&n2=v%252");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:null queryString:n1=v%251&n2=v%252 servletPath:/other/pa%th.info requestUri:/context/other/pa%25th.info;p1=v1;p2=v2", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testEncodedPathParamsEncoded() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/context/path/path%25info;p1=v%251;p2=v2?n1=v%251&n2=v%252");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:/path%info queryString:n1=v%251&n2=v%252 servletPath:/path requestUri:/context/path/path%25info;p1=v%251;p2=v2", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testEncodedExtensionPathParamsEncoded() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/context/other/pa%25th.info;p1=v%251;p2=v2?n1=v%251&n2=v%252");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:null queryString:n1=v%251&n2=v%252 servletPath:/other/pa%th.info requestUri:/context/other/pa%25th.info;p1=v%251;p2=v2", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
