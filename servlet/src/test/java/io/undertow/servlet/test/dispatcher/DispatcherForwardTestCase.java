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
import io.undertow.servlet.test.dispatcher.util.DispatcherUtil;
import io.undertow.servlet.test.util.MessageFilter;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.ParameterEchoServlet;
import io.undertow.servlet.test.util.PathTestServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.wildfly.common.Assert.assertTrue;

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
        //we don't run this test on h2 upgrade, as if it is run with the original request
        //the protocols will not match
        Assume.assumeFalse(DefaultServer.isH2upgrade());
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
                                .addMapping("/dispatch").addMapping("/dispatch/*").addMapping("*.dispatch"))
                .addServlet(
                        new ServletInfo("pathTest", PathTestServlet.class)
                                .addMapping("/path"))
                .addServlet(
                        new ServletInfo("pathForwardTest", ForwardPathTestServlet.class)
                                .addMapping("/path-forward").addMapping("/path-forward/*").addMapping("*.forwardinfo"))
                .addServlet(
                        new ServletInfo("parameterEcho", ParameterEchoServlet.class)
                                .addMapping("/echo-parameters"))
                .addServlet(
                        new ServletInfo("/dispatchServlet", DispatcherForwardServlet.class)
                                .addMapping("/dispatchServlet"))
                .addServlet(
                        new ServletInfo("/next", NextServlet.class)
                                .addMapping("/next"))
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
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("forward", "/forward");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                assertEquals("Path!Name!forwarded", response);
                return null;
            });
            latch.await(30, TimeUnit.SECONDS);
            //UNDERTOW-327 make sure that the access log includes the original path
            String protocol = DefaultServer.isH2() ? Protocols.HTTP_2_0_STRING : Protocols.HTTP_1_1_STRING;
            assertEquals("GET /servletContext/dispatch " + protocol + " /servletContext/dispatch /dispatch", message);
        }
    }

    private void resetLatch() {
        latch.countDown();
        latch = new CountDownLatch(1);
    }

    @Test
    public void testNameBasedInclude() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("forward", "forward");
            get.setHeader("name", "true");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                assertEquals("Name!forwarded", response);
                return null;
            });
        }
    }

    @Test
    public void testNameBasedForwardOutServletContext() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("forward", "../forward");
            client.execute(get, result -> {
                assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                MatcherAssert.assertThat(response, CoreMatchers.containsString("dispatcher was null!"));
                return null;
            });
        }
    }

    @Test
    public void testPathBasedStaticInclude() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("forward", "/snippet.html");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                assertEquals("SnippetText", response);
                return null;
            });
        }
    }

    @Test
    public void testPathBasedStaticIncludePost() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            post.setHeader("forward", "/snippet.html");
            client.execute(post, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                assertEquals("SnippetText", response);
                return null;
            });
        }
    }


    @Test
    public void testIncludeAggregatesQueryString() throws IOException, InterruptedException {
        String protocol = DefaultServer.isH2() ? Protocols.HTTP_2_0_STRING : Protocols.HTTP_1_1_STRING;
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            resetLatch();
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?a=b");
            get.setHeader("forward", "/path");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                assertEquals("pathInfo:null queryString:a=b servletPath:/path requestUri:/servletContext/path", response);
                return null;
            });
            latch.await(30, TimeUnit.SECONDS);
            //UNDERTOW-327 and UNDERTOW-1599 - make sure that the access log includes the original path and query string
            assertEquals("GET /servletContext/dispatch?a=b " + protocol + " /servletContext/dispatch /dispatch", message);

            resetLatch();
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?a=b");
            get.setHeader("forward", "/path?foo=bar");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                assertEquals("pathInfo:null queryString:foo=bar servletPath:/path requestUri:/servletContext/path", response);
                return null;
            });
            latch.await(30, TimeUnit.SECONDS);
            //UNDERTOW-327 and UNDERTOW-1599 - make sure that the access log includes the original path and query string
            assertEquals("GET /servletContext/dispatch?a=b " + protocol + " /servletContext/dispatch /dispatch", message);
        }
    }


    @Test
    public void testIncludesPathParameters() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?a=b");
            get.setHeader("forward", "/path;pathparam=foo");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                assertEquals("pathInfo:null queryString:a=b servletPath:/path requestUri:/servletContext/path;pathparam=foo", response);
                return null;
            });
        }
    }

    @Test
    public void testIncludesUrlInPathParameters() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("forward", "/path?url=http://test.com");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                // Path parameters should not be canonicalized
                assertEquals("pathInfo:null queryString:url=http://test.com servletPath:/path requestUri:/servletContext/path", response);
                return null;
            });
        }
    }

    @Test
    public void testAttributes() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?n1=v1&n2=v2");
            get.setHeader("forward", "/path-forward");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                MatcherAssert.assertThat(response, CoreMatchers.containsString("pathInfo:null queryString:n1=v1&n2=v2 servletPath:/path-forward requestUri:/servletContext/path-forward\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.request_uri:/servletContext/dispatch\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.context_path:/servletContext\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.servlet_path:/dispatch\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.path_info:null\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.query_string:n1=v1&n2=v2\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.mapping:"));
                return null;
            });
        }
    }

    @Test
    public void testAttributesEncoded() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch/dispatch%25info?n1=v%251&n2=v%252");
            get.setHeader("forward", "/path-forward/path%25info?n3=V%253");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                MatcherAssert.assertThat(response, CoreMatchers.containsString("pathInfo:/path%info queryString:n3=V%253 servletPath:/path-forward requestUri:/servletContext/path-forward/path%25info\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.request_uri:/servletContext/dispatch/dispatch%25info\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.context_path:/servletContext\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.servlet_path:/dispatch\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.path_info:/dispatch%info\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.query_string:n1=v%251&n2=v%252\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.mapping:"));
                return null;
            });
        }
    }

    @Test
    public void testAttributesEncodedExtension() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dis%25patch/dispatch%25info.dispatch?n1=v%251&n2=v%252");
            get.setHeader("forward", "/to%25forward/path%25info.forwardinfo?n3=V%253");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                MatcherAssert.assertThat(response, CoreMatchers.containsString("pathInfo:null queryString:n3=V%253 servletPath:/to%forward/path%info.forwardinfo requestUri:/servletContext/to%25forward/path%25info.forwardinfo\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.request_uri:/servletContext/dis%25patch/dispatch%25info.dispatch\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.context_path:/servletContext\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.servlet_path:/dis%patch/dispatch%info.dispatch\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.path_info:null\r\n"));
                MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.forward.query_string:n1=v%251&n2=v%252\r\n"));
                return null;
            });
        }
    }

    @Test
    public void testParametersAreMerged() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?param1=v11&param1=v12");
            get.setHeader("forward", "/echo-parameters?param1=v13&param1=v14");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                assertEquals("param1='v13,v14,v11,v12'", response);
                return null;
            });
        }
    }

    @Test
    public void testParametersAreMergedButNotDuplicated() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?param1=v11&param1=v12");
            get.setHeader("forward", "/echo-parameters?param1=v11&param1=v13&param1=v14");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                assertEquals("param1='v11,v13,v14,v12'", response);
                return null;
            });
        }
    }

    @Test
    public void testDisptacherServletForward() throws IOException, InterruptedException {
        resetLatch();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatchServlet");
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                //UNDERTOW-2245 javax.servlet.forward.mapping request attribute is not available in servlets forwarded with RequestDispatcher

                assertTrue(DispatcherUtil.containsWord(response, "jakarta.servlet.forward.context_path"));
                assertTrue(DispatcherUtil.containsWord(response, "jakarta.servlet.forward.servlet_path"));
                assertTrue(DispatcherUtil.containsWord(response, "jakarta.servlet.forward.request_uri"));
                assertTrue(DispatcherUtil.containsWord(response, "jakarta.servlet.forward.mapping"));
                return null;
            });
        }
    }
}
