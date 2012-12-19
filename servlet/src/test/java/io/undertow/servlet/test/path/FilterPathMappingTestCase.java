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

package io.undertow.servlet.test.path;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import io.undertow.util.TestHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class FilterPathMappingTestCase {

    @Test
    public void testBasicFilterMappings() throws IOException, ServletException {

        DeploymentInfo builder = new DeploymentInfo();

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        builder.addServlet(new ServletInfo("/a/*", PathMappingServlet.class)
                .addMapping("/a/*"));

        builder.addServlet(new ServletInfo("/aa", PathMappingServlet.class)
                .addMapping("/aa"));

        builder.addServlet(new ServletInfo("/", PathMappingServlet.class)
                .addMapping("/"));

        builder.addServlet(new ServletInfo("contextRoot", PathMappingServlet.class)
                .addMapping(""));

        builder.addFilter(new FilterInfo("/*", PathFilter.class));
        builder.addFilterUrlMapping("/*", "/*", DispatcherType.REQUEST);

        builder.addFilter(new FilterInfo("/a/*", PathFilter.class));
        builder.addFilterUrlMapping("/a/*", "/a/*", DispatcherType.REQUEST);

        builder.addFilter(new FilterInfo("/aa", PathFilter.class));
        builder.addFilterUrlMapping("/aa", "/aa", DispatcherType.REQUEST);

        builder.addFilter(new FilterInfo("*.bop", PathFilter.class));
        builder.addFilterUrlMapping("*.bop", "*.bop", DispatcherType.REQUEST);

        builder.addFilter(new FilterInfo("contextRoot", PathFilter.class));
        builder.addFilterServletNameMapping("contextRoot", "contextRoot", DispatcherType.REQUEST);

        builder.setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(FilterPathMappingTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER);

        final DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);



        TestHttpClient client = new TestHttpClient();
        try {
            runTest(client, "aa","/aa", "/*", "/aa");
            runTest(client, "a/c","/a/*", "/*", "/a/*");
            runTest(client, "a","/a/*", "/*", "/a/*");
            runTest(client, "aa/b","/", "/*");
            runTest(client, "a/b/c/d","/a/*", "/*", "/a/*");
            runTest(client, "defaultStuff","/", "/*");
            runTest(client, "","contextRoot", "/*", "contextRoot");
            runTest(client, "yyyy.bop","/", "/*", "*.bop");

        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testExtensionMatchServletWithGlobalFilter() throws IOException, ServletException {

        DeploymentInfo builder = new DeploymentInfo();

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        builder.addServlet(new ServletInfo("*.jsp", PathMappingServlet.class)
                .addMapping("*.jsp"));

        builder.addFilter(new FilterInfo("/*", PathFilter.class));
        builder.addFilterUrlMapping("/*", "/*", DispatcherType.REQUEST);

        builder.setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(FilterPathMappingTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER);

        final DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);


        TestHttpClient client = new TestHttpClient();
        try {
            runTest(client, "aa.jsp","*.jsp", "/*");

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void runTest(final TestHttpClient client, final String path, final String expected, final String ... headers) throws IOException {
        final HttpGet get;
        final HttpResponse result;
        final String response;
        get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/" + path);
        result = client.execute(get);
        Assert.assertEquals(200, result.getStatusLine().getStatusCode());
        requireHeaders(result, headers);
        response = HttpClientUtils.readResponse(result);
        Assert.assertEquals(expected, response);
    }

    private void requireHeaders(final HttpResponse result, final String... headers) {
        final Header[] resultHeaders = result.getAllHeaders();
        final List<Header> realResultHeaders = new ArrayList<Header>();
        for(Header header: resultHeaders) {
            if(header.getName().startsWith("filter")) {
                realResultHeaders.add(header);
            }
        }
        final Set<String> found = new HashSet<String>(Arrays.asList(headers));
        for (Header header : realResultHeaders) {
            if (!found.remove(header.getValue())) {
                Assert.fail("Found unexpected header " + header.getValue());
            }
        }
        if (!found.isEmpty()) {
            Assert.fail("header(s) not found " + found);
        }
    }

}
