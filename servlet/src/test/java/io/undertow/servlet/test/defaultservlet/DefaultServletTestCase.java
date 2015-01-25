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

package io.undertow.servlet.test.defaultservlet;

import java.io.IOException;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.handlers.DefaultServlet;
import io.undertow.servlet.test.path.ServletPathMappingTestCase;
import io.undertow.servlet.test.util.PathTestServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
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
public class DefaultServletTestCase {


    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ServletPathMappingTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setResourceManager(new TestResourceLoader(DefaultServletTestCase.class));

        builder.addServlet(new ServletInfo("DefaultTestServlet", PathTestServlet.class)
                .addMapping("/path/default"));

        builder.addServlet(new ServletInfo("default", DefaultServlet.class)
                .addInitParam("directory-listing", "true")
                .addMapping("/*"));


        builder.addFilter(new FilterInfo("Filter", HelloFilter.class));
        builder.addFilterUrlMapping("Filter", "/filterpath/*", DispatcherType.REQUEST);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testSimpleResource() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/index.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.contains("Redirected home page"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testRangeRequest() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/index.html");
            get.addHeader("range", "bytes=2-3");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("--", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testResourceWithFilter() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/filterpath/filtered.txt");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Hello Stuart", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testDisallowedResource() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/disallowed.sh");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testDirectoryListing() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }



    @Test
    public void testIfMoodifiedSince() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/index.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.contains("Redirected home page"));

            String lm = result.getHeaders("Last-Modified")[0].getValue();
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/index.html");
            get.addHeader("IF-Modified-Since", lm);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_MODIFIED, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertFalse(response.contains("Redirected home page"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
