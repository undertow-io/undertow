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

package io.undertow.servlet.test.request;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.SetHeaderFilter;
import io.undertow.servlet.test.util.TestClassIntrospector;
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
public class RequestPathTestCase {

    @BeforeClass
    public static void setup() throws ServletException {


        final PathHandler pathHandler = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlets(
                        new ServletInfo("request", RequestPathServlet.class)
                                .addMapping("/req/*"),
                        new ServletInfo("DefaultServlet", RequestPathServlet.class)
                                .addMapping("/"),
                        new ServletInfo("ExactServlet", RequestPathServlet.class)
                                .addMapping("/exact"),
                        new ServletInfo("ExactTxtServlet", RequestPathServlet.class)
                                .addMapping("/exact.txt"),
                        new ServletInfo("HtmlServlet", RequestPathServlet.class)
                                .addMapping("*.html")
                )
                .addFilters(
                        new FilterInfo("header", SetHeaderFilter.class)
                                .addInitParam("header", "Filter").addInitParam("value", "true"),
                        new FilterInfo("all", SetHeaderFilter.class)
                                .addInitParam("header", "all").addInitParam("value", "true"))
                .addFilterUrlMapping("header", "*.txt", DispatcherType.REQUEST)
                .addFilterUrlMapping("all", "/*", DispatcherType.REQUEST);
        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        try {
            pathHandler.addPrefixPath(builder.getContextPath(), manager.start());
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        DefaultServer.setRootHandler(pathHandler);

    }

    @Test
    public void testRequestPaths() throws Exception {
        int port = DefaultServer.getHostPort("default");
        final String hostAddress = DefaultServer.getHostAddress();

        //test default servlet mappings
        runtest("/servletContext/somePath", false, "null", "/somePath", "http://"+ hostAddress + ":" + port + "/servletContext/somePath", "/servletContext/somePath", "");
        runtest("/servletContext/somePath?foo=bar", false, "null", "/somePath", "http://" + hostAddress + ":" + port + "/servletContext/somePath", "/servletContext/somePath", "foo=bar");
        runtest("/servletContext/somePath?foo=b+a+r", false, "null", "/somePath", "http://" + hostAddress + ":" + port + "/servletContext/somePath", "/servletContext/somePath", "foo=b+a+r");
        runtest("/servletContext/some%20path?foo=b+a+r", false, "null", "/some path", "http://" + hostAddress + ":" + port + "/servletContext/some%20path", "/servletContext/some%20path", "foo=b+a+r");
        runtest("/servletContext/somePath.txt", true, "null", "/somePath.txt", "http://" + hostAddress + ":" + port + "/servletContext/somePath.txt", "/servletContext/somePath.txt", "");
        runtest("/servletContext/somePath.txt?foo=bar", true, "null", "/somePath.txt", "http://" + hostAddress + ":" + port + "/servletContext/somePath.txt", "/servletContext/somePath.txt", "foo=bar");

        //test non-default mappings
        runtest("/servletContext/req/somePath", false, "/somePath", "/req", "http://" + hostAddress + ":" + port + "/servletContext/req/somePath", "/servletContext/req/somePath", "");
        runtest("/servletContext/req/somePath?foo=bar", false, "/somePath", "/req", "http://" + hostAddress + ":" + port + "/servletContext/req/somePath", "/servletContext/req/somePath", "foo=bar");
        runtest("/servletContext/req/somePath?foo=b+a+r", false, "/somePath", "/req", "http://" + hostAddress + ":" + port + "/servletContext/req/somePath", "/servletContext/req/somePath", "foo=b+a+r");
        runtest("/servletContext/req/some%20path?foo=b+a+r", false, "/some path", "/req", "http://" + hostAddress + ":" + port + "/servletContext/req/some%20path", "/servletContext/req/some%20path", "foo=b+a+r");
        runtest("/servletContext/req/somePath.txt", true, "/somePath.txt", "/req", "http://" + hostAddress + ":" + port + "/servletContext/req/somePath.txt", "/servletContext/req/somePath.txt", "");
        runtest("/servletContext/req/somePath.txt?foo=bar", true, "/somePath.txt", "/req", "http://" + hostAddress + ":" + port + "/servletContext/req/somePath.txt", "/servletContext/req/somePath.txt", "foo=bar");

        //test exact path mappings
        runtest("/servletContext/exact", false, "null", "/exact", "http://" + hostAddress + ":" + port + "/servletContext/exact", "/servletContext/exact", "");
        runtest("/servletContext/exact?foo=bar", false, "null", "/exact", "http://" + hostAddress + ":" + port + "/servletContext/exact", "/servletContext/exact", "foo=bar");

        //test exact path mappings with a filer
        runtest("/servletContext/exact.txt", true, "null", "/exact.txt", "http://" + hostAddress + ":" + port + "/servletContext/exact.txt", "/servletContext/exact.txt", "");
        runtest("/servletContext/exact.txt?foo=bar", true, "null", "/exact.txt", "http://" + hostAddress + ":" + port + "/servletContext/exact.txt", "/servletContext/exact.txt", "foo=bar");

        //test servlet extension matches
        runtest("/servletContext/file.html", false, "null", "/file.html", "http://" + hostAddress + ":" + port + "/servletContext/file.html", "/servletContext/file.html", "");
        runtest("/servletContext/file.html?foo=bar", false, "null", "/file.html", "http://" + hostAddress + ":" + port + "/servletContext/file.html", "/servletContext/file.html", "foo=bar");
    }

    private void runtest(String request, boolean filterHeader, String... expectedBody) throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + request);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);

            Assert.assertArrayEquals(expectedBody, split(response));
            Assert.assertEquals("true", result.getHeaders("all")[0].getValue());
            if (filterHeader) {
                Assert.assertEquals("true", result.getHeaders("Filter")[0].getValue());
            } else {
                Assert.assertEquals(0, result.getHeaders("Filter").length);
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    /**
     * because String.split() is retarded
     */
    private static String[] split(String s) {
        List<String> strings = new ArrayList<>();
        int pos = 0;
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c == ',') {
                strings.add(s.substring(pos, i));
                pos = i + 1;
            }
        }
        strings.add(s.substring(pos));
        return strings.toArray(new String[strings.size()]);
    }

}
