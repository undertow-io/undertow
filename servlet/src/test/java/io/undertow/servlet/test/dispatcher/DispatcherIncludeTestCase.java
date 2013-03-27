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

package io.undertow.servlet.test.dispatcher;

import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.MessageFilter;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

import io.undertow.util.TestHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

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
                .setResourceLoader(new TestResourceLoader(DispatcherIncludeTestCase.class))
                .addServlet(
                        new ServletInfo("include", MessageServlet.class)
                                .addInitParam(MessageServlet.MESSAGE, "included")
                                .addMapping("/include"))
                .addServlet(
                        new ServletInfo("dispatcher", IncludeServlet.class)
                                .addMapping("/dispatch"))
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
        root.addPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testPathBasedInclude() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            get.setHeader("include", "/include");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(IncludeServlet.MESSAGE + "Path!Name!included", response);
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
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(IncludeServlet.MESSAGE + "Name!included", response);
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
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(IncludeServlet.MESSAGE + "SnippetText", response);
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
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(IncludeServlet.MESSAGE + "SnippetText", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
