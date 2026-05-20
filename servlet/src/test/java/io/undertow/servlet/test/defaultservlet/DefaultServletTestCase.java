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
import java.util.Date;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;

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
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
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

        //see UNDERTOW-458
        builder.addFilter(new FilterInfo("date-header", GetDateFilter.class));
        builder.addFilterUrlMapping("date-header", "/*", DispatcherType.REQUEST);


        builder.addFilter(new FilterInfo("Filter", HelloFilter.class));
        builder.addFilterUrlMapping("Filter", "/filterpath/*", DispatcherType.REQUEST);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testSimpleResource() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/index.html");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.contains("Redirected home page"));
                return null;
            });
        }
    }

    @Test
    public void testRangeRequest() throws IOException, InterruptedException {
        String uri = DefaultServer.getDefaultServerURL() + "/servletContext/range.txt";
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/index.html");
            get.addHeader(Headers.RANGE_STRING, "bytes=2-3");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("--", response);
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=3-100");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("3456789", response);
                Assert.assertEquals("bytes 3-9/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=3-9");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("3456789", response);
                Assert.assertEquals("bytes 3-9/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=2-3");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("23", response);
                Assert.assertEquals("bytes 2-3/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=0-0");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("0", response);
                Assert.assertEquals("bytes 0-0/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=1-");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("123456789", response);
                Assert.assertEquals("bytes 1-9/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=0-");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("0123456789", response);
                Assert.assertEquals("bytes 0-9/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=9-");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("9", response);
                Assert.assertEquals("bytes 9-9/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=-1");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("9", response);
                Assert.assertEquals("bytes 9-9/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=-100");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("0123456789", response);
                Assert.assertEquals("bytes 0-9/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=99-100");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.REQUEST_RANGE_NOT_SATISFIABLE, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("", response);
                Assert.assertEquals("bytes */10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=2-1");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.REQUEST_RANGE_NOT_SATISFIABLE, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("", response);
                Assert.assertEquals("bytes */10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });
            //test if-range

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=2-3");
            get.addHeader(Headers.IF_RANGE_STRING, DateUtils.toDateString(new Date(System.currentTimeMillis() + 1000)));
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("23", response);
                Assert.assertEquals("bytes 2-3/10", result.getFirstHeader(Headers.CONTENT_RANGE_STRING).getValue());
                return null;
            });

            get = new HttpGet(uri);
            get.addHeader(Headers.RANGE_STRING, "bytes=2-3");
            get.addHeader(Headers.IF_RANGE_STRING, DateUtils.toDateString(new Date(0)));
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = EntityUtils.toString(result.getEntity());
                Assert.assertEquals("0123456789", response);
                Assert.assertNull(result.getFirstHeader(Headers.CONTENT_RANGE_STRING));
                return null;
            });
        }
    }

    @Test
    public void testResourceWithFilter() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/filterpath/filtered.txt");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("Hello Stuart", response);
                return null;
            });
        }
    }

    @Test
    public void testDisallowedResource() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/disallowed.sh");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                return HttpClientUtils.readResponse(result);
            });
        }
    }

    @Test
    public void testNoAccessToMetaInfResource() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/meta-inf/secret");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.NOT_FOUND, result.getCode());
                return null;
            });
        }
    }

    @Test
    public void testAccessToMetaInfSubDirResource() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo/meta-inf/notsecret");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                return null;
            });
        }
    }

    @Test
    public void testAccessToWebInfSubDirResource() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo/web-inf/notsecret");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                return null;
            });
        }
    }

    @Test
    public void testDirectoryListing() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path"), result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertNotNull(result.getFirstHeader(Headers.CONTENT_TYPE_STRING));
                MatcherAssert.assertThat(result.getFirstHeader(Headers.CONTENT_TYPE_STRING).getValue(), CoreMatchers.startsWith("text/html"));
                MatcherAssert.assertThat(HttpClientUtils.readResponse(result), CoreMatchers.containsString(".gitkeep"));
                return null;
            });
            client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path?js"), result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertNotNull(result.getFirstHeader(Headers.CONTENT_TYPE_STRING));
                MatcherAssert.assertThat(result.getFirstHeader(Headers.CONTENT_TYPE_STRING).getValue(), CoreMatchers.startsWith("application/javascript"));
                MatcherAssert.assertThat(HttpClientUtils.readResponse(result), CoreMatchers.containsString("growit()"));
                return null;
            });
            client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path?css"), result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertNotNull(result.getFirstHeader(Headers.CONTENT_TYPE_STRING));
                MatcherAssert.assertThat(result.getFirstHeader(Headers.CONTENT_TYPE_STRING).getValue(), CoreMatchers.startsWith("text/css"));
                MatcherAssert.assertThat(HttpClientUtils.readResponse(result), CoreMatchers.containsString("data:image/png;base64"));
                return null;
            });
        }
    }

    @Test
    public void testIfMoodifiedSince() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/index.html");
            //UNDERTOW-458
            get.addHeader("date-header", "Fri, 10 Oct 2014 21:35:55 CEST");
            String lm = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.contains("Redirected home page"));

                return result.getHeaders("Last-Modified")[0].getValue();
            });
            System.out.println(lm);
            Assert.assertTrue(lm.endsWith("GMT"));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/index.html");
            get.addHeader("IF-Modified-Since", lm);
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.NOT_MODIFIED, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertFalse(response.contains("Redirected home page"));
                return null;
            });
        }
    }

}
