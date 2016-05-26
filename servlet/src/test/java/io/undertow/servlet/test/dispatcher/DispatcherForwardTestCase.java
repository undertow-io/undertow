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

package io.undertow.servlet.test.dispatcher;

import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.accesslog.AccessLogFileTestCase;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.accesslog.AccessLogReceiver;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.MessageFilter;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.PathTestServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class DispatcherForwardTestCase {

    private static volatile String message;

    private static volatile CountDownLatch latch = new CountDownLatch(1);


    private static final AccessLogReceiver RECEIVER = new AccessLogReceiver() {

        @Override
        public void logMessage(final String msg) {
            message = msg;
            latch.countDown();
        }
    };

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setResourceManager(new TestResourceLoader(DispatcherForwardTestCase.class))
                .addServlet(
                        new ServletInfo("forward", MessageServlet.class)
                                .addInitParam(MessageServlet.MESSAGE, "forwarded")
                                .addMapping("/forward"))
                .addServlet(
                        new ServletInfo("dispatcher", ForwardServlet.class)
                                .addMapping("/dispatch"))
                .addServlet(
                        new ServletInfo("pathTest", PathTestServlet.class)
                                .addMapping("/path"))
                .addFilter(
                        new FilterInfo("notforwarded", MessageFilter.class)
                                .addInitParam(MessageFilter.MESSAGE, "Not forwarded"))
                .addFilter(
                        new FilterInfo("inc", MessageFilter.class)
                                .addInitParam(MessageFilter.MESSAGE, "Path!"))
                .addFilter(
                        new FilterInfo("nameFilter", MessageFilter.class)
                                .addInitParam(MessageFilter.MESSAGE, "Name!"))
                .addFilterUrlMapping("notforwarded", "/forward", DispatcherType.REQUEST)
                .addFilterUrlMapping("inc", "/forward", DispatcherType.FORWARD)
                .addFilterServletNameMapping("nameFilter", "forward", DispatcherType.FORWARD);


        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(new AccessLogHandler(root, RECEIVER, "%r %U %R", AccessLogFileTestCase.class.getClassLoader()));
    }

    @Test
    public void testPathBasedInclude() throws IOException, InterruptedException {
        resetLatch();
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("forward", "/forward");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Path!Name!forwarded", response);
            latch.await(30, TimeUnit.SECONDS);
            //UNDERTOW-327 make sure that the access log includes the original path
            String protocol = DefaultServer.isH2() ? Protocols.HTTP_2_0_STRING : Protocols.HTTP_1_1_STRING;
            Assert.assertEquals("GET /servletContext/dispatch " + protocol + " /servletContext/dispatch /dispatch", message);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void resetLatch() {
        latch.countDown();
        latch = new CountDownLatch(1);
    }

    @Test
    public void testNameBasedInclude() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("forward", "forward");
            get.setHeader("name", "true");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Name!forwarded", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testPathBasedStaticInclude() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("forward", "/snippet.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("SnippetText", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testPathBasedStaticIncludePost() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            post.setHeader("forward", "/snippet.html");
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("SnippetText", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testIncludeAggregatesQueryString() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?a=b");
            get.setHeader("forward", "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:null queryString:a=b servletPath:/path requestUri:/servletContext/path", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?a=b");
            get.setHeader("forward", "/path?foo=bar");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:null queryString:foo=bar servletPath:/path requestUri:/servletContext/path", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }



    @Test
    public void testIncludesPathParameters() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?a=b");
            get.setHeader("forward", "/path;pathparam=foo");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("pathInfo:null queryString:a=b servletPath:/path requestUri:/servletContext/path;pathparam=foo", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
