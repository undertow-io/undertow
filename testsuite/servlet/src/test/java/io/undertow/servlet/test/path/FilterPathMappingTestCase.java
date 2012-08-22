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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.test.shared.DefaultServer;
import io.undertow.test.shared.HttpClientUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class FilterPathMappingTestCase {


    @BeforeClass
    public static void setup() {

        final PathHandler root = new PathHandler();
        final ServletContainer container = new ServletContainer(root);

        ServletInfo.ServletInfoBuilder aStar = ServletInfo.builder()
                .setName("/a/*")
                .setServletClass(PathMappingServlet.class)
                .addMapping("/a/*");

        ServletInfo.ServletInfoBuilder aa = ServletInfo.builder()
                .setName("/aa")
                .setServletClass(PathMappingServlet.class)
                .addMapping("/aa");

        ServletInfo.ServletInfoBuilder d = ServletInfo.builder()
                .setName("/")
                .setServletClass(PathMappingServlet.class)
                .addMapping("/");


        ServletInfo.ServletInfoBuilder cr = ServletInfo.builder()
                .setName("contextRoot")
                .setServletClass(PathMappingServlet.class)
                .addMapping("");

        FilterInfo.FilterInfoBuilder f1 = FilterInfo.builder()
                .setFilterClass(PathFilter.class)
                .setName("/*")
                .addUrlMapping("/*");


        FilterInfo.FilterInfoBuilder f2 = FilterInfo.builder()
                .setFilterClass(PathFilter.class)
                .setName("/a/*")
                .addUrlMapping("/a/*");

        FilterInfo.FilterInfoBuilder f3 = FilterInfo.builder()
                .setFilterClass(PathFilter.class)
                .setName("/aa")
                .addUrlMapping("/aa");


        FilterInfo.FilterInfoBuilder f4 = FilterInfo.builder()
                .setFilterClass(PathFilter.class)
                .setName("contextRoot")
                .addServletNameMapping("contextRoot");

        DeploymentInfo.DeploymentInfoBuilder builder = DeploymentInfo.builder()
                .setClassLoader(FilterPathMappingTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setResourceLoader(TestResourceLoader.INSTANCE)
                .addServlets(aStar, aa, d, cr)
                .addFilters(f1, f2, f3, f4);

        DeploymentManager manager = container.addDeployment(builder.build());
        manager.deploy();
        manager.start();

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testSimpleHttpServlet() throws IOException {
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/aa");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/aa", response);
            requireHeaders(result, "/*", "/aa");

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/a/c");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            requireHeaders(result, "/*", "/a/*");
            Assert.assertEquals("/a/*", response);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/a");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            requireHeaders(result, "/*", "/a/*");
            Assert.assertEquals("/a/*", response);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/aa/b");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            requireHeaders(result, "/*");
            Assert.assertEquals("/", response);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/a/b/c/d");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            requireHeaders(result, "/*", "/a/*");
            Assert.assertEquals("/a/*", response);


            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/defaultStuff");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            requireHeaders(result, "/*");
            Assert.assertEquals("/", response);


            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            requireHeaders(result, "/*", "contextRoot");
            Assert.assertEquals("contextRoot", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void requireHeaders(final HttpResponse result, final String... headers) {
        final Header[] resultHeaders = result.getHeaders("filter");
        final Set<String> found = new HashSet<String>(Arrays.asList(headers));
        for(Header header : resultHeaders) {
            if(!found.remove(header.getValue())) {
                Assert.fail("Found unexpected header " + header.getValue());
            }
        }
        if(!found.isEmpty()) {
            Assert.fail("header(s) not found " + found);
        }
    }

}
