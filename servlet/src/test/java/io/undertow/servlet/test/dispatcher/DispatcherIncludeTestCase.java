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
import io.undertow.util.StatusCodes;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.wildfly.common.Assert.assertTrue;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class DispatcherIncludeTestCase {


    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setResourceManager(new TestResourceLoader(DispatcherIncludeTestCase.class))
                .addServlet(
                        new ServletInfo("include", MessageServlet.class)
                                .addInitParam(MessageServlet.MESSAGE, "included")
                                .addMapping("/include"))
                .addServlet(
                        new ServletInfo("dispatcher", IncludeServlet.class)
                                .addMapping("/dispatch").addMapping("/dispatch/*").addMapping("*.dispatch"))
                .addServlet(
                        new ServletInfo("pathTest", PathTestServlet.class)
                                .addMapping("/path"))
                .addServlet(
                        new ServletInfo("pathIncludeTest", IncludePathTestServlet.class)
                                .addMapping("/path-include").addMapping("/path-include/*").addMapping("*.includeinfo"))
                .addServlet(
                        new ServletInfo("parameterEcho", ParameterEchoServlet.class)
                                .addMapping("/echo-parameters"))
                .addServlet(
                        new ServletInfo("/dispatchServletInclude", DispatcherIncludeServlet.class)
                                .addMapping("/dispatchServletInclude"))
                .addServlet(
                        new ServletInfo("/next", NextServlet.class)
                                .addMapping("/next"))
                .addFilter(
                        new FilterInfo("notIncluded", MessageFilter.class)
                                .addInitParam(MessageFilter.MESSAGE, "Not Included"))
                .addFilter(
                        new FilterInfo("inc", MessageFilter.class)
                                .addInitParam(MessageFilter.MESSAGE, "Path!"))
                .addFilter(
                        new FilterInfo("nameFilter", MessageFilter.class)
                                .addInitParam(MessageFilter.MESSAGE, "Name!"))
                .addFilterUrlMapping("notIncluded", "/include", DispatcherType.REQUEST)
                .addFilterUrlMapping("inc", "/include", DispatcherType.INCLUDE)
                .addFilterServletNameMapping("nameFilter", "include", DispatcherType.INCLUDE);


        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testPathBasedInclude() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("include", "/include");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            assertEquals(IncludeServlet.MESSAGE + "Path!Name!included", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testNameBasedInclude() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("include", "include");
            get.setHeader("name", "true");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            assertEquals(IncludeServlet.MESSAGE + "Name!included", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testPathBasedStaticInclude() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("include", "/snippet.html");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            assertEquals(IncludeServlet.MESSAGE + "SnippetText", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testPathBasedStaticIncludePost() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            post.setHeader("include", "/snippet.html");
            HttpResponse result = client.execute(post);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            assertEquals(IncludeServlet.MESSAGE + "SnippetText", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testIncludeAggregatesQueryString() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?a=b");
            get.setHeader("include", "/path");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            assertEquals(IncludeServlet.MESSAGE + "pathInfo:null queryString:a=b servletPath:/dispatch requestUri:/servletContext/dispatch", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?a=b");
            get.setHeader("include", "/path?foo=bar");
            result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            assertEquals(IncludeServlet.MESSAGE + "pathInfo:null queryString:a=b servletPath:/dispatch requestUri:/servletContext/dispatch", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testAttributes() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?n1=v1&n2=v2");
            get.setHeader("include", "/path-include?url=http://test.com");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            MatcherAssert.assertThat(response, CoreMatchers.containsString(IncludeServlet.MESSAGE + "pathInfo:null queryString:n1=v1&n2=v2 servletPath:/dispatch requestUri:/servletContext/dispatch\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.request_uri:/servletContext/path-include\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.context_path:/servletContext\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.servlet_path:/path-include\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.path_info:null\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.query_string:url=http://test.com\r\n"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testAttributesEncoded() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch/dis%25patch?n1=v1&n2=v%252");
            get.setHeader("include", "/path-include/path%25include?n2=v%253");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            MatcherAssert.assertThat(response, CoreMatchers.containsString(IncludeServlet.MESSAGE + "pathInfo:/dis%patch queryString:n1=v1&n2=v%252 servletPath:/dispatch requestUri:/servletContext/dispatch/dis%25patch\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.request_uri:/servletContext/path-include/path%25include\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.context_path:/servletContext\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.servlet_path:/path-include\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.path_info:/path%include\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.query_string:n2=v%253\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.mapping:"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testAttributesEncodedExtension() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dis%25patch.dispatch?n1=v1&n2=v%252");
            get.setHeader("include", "/path%25include.includeinfo?n2=v%253");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            MatcherAssert.assertThat(response, CoreMatchers.containsString(IncludeServlet.MESSAGE + "pathInfo:null queryString:n1=v1&n2=v%252 servletPath:/dis%patch.dispatch requestUri:/servletContext/dis%25patch.dispatch\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.request_uri:/servletContext/path%25include.includeinfo\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.context_path:/servletContext\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.servlet_path:/path%include.includeinfo\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.path_info:null\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.query_string:n2=v%253\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.mapping:"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testParametersAreMerged() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch?param1=v11&param1=v12");
            get.setHeader("include", "/echo-parameters?param1=v13&param1=v14");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            assertEquals(IncludeServlet.MESSAGE + "param1='v13,v14,v11,v12'", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
    @Test
    public void testDisptacherServletInclude() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatchServletInclude");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            //UNDERTOW-2245 javax.servlet.forward.mapping request attribute is not available in servlets forwarded with RequestDispatcher

            assertTrue(DispatcherUtil.containsWord(response,"jakarta.servlet.include.context_path"));
            assertTrue(DispatcherUtil.containsWord(response,"jakarta.servlet.include.servlet_path"));
            assertTrue(DispatcherUtil.containsWord(response,"jakarta.servlet.include.request_uri"));
            assertTrue(DispatcherUtil.containsWord(response,"jakarta.servlet.include.mapping"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testDisptacherServletIncludeNest() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dis%25patch.dispatch?n1=v1&n2=v%252");
            String include = URLEncoder.encode("/path%25include.includeinfo?n4=v4", StandardCharsets.UTF_8);
            get.setHeader("include", "/servletContext/dis%25patch.dispatch?n3=v3&include=" + include);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            MatcherAssert.assertThat(response, CoreMatchers.containsString(IncludeServlet.MESSAGE + "pathInfo:null queryString:n1=v1&n2=v%252 servletPath:/dis%patch.dispatch requestUri:/servletContext/dis%25patch.dispatch\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.request_uri:/servletContext/path%25include.includeinfo\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.context_path:/servletContext\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.servlet_path:/path%include.includeinfo\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.path_info:null\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.query_string:n4=v4\r\n"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("jakarta.servlet.include.mapping:"));
            MatcherAssert.assertThat(response, CoreMatchers.containsString("request params:include=/path%25include.includeinfo?n4=v4n1=v1n2=v%2n3=v3n4=v4\r\n"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
