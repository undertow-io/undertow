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

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.SimpleServletTestCase;
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

import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;

import static io.undertow.servlet.Servlets.servlet;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class RedirectTestCase {

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
                        servlet("request", RequestPathServlet.class)
                                .addMapping("/"),
                        servlet("redirect", RedirectServlet.class)
                                .addMapping("/redirect/*"));
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
    public void testServletRedirect() throws Exception {
        int port = DefaultServer.getHostPort("default");
        //test redirects
        runtest("/servletContext/redirect/foo?redirect=../bar", "null", "/bar",
                "http://" + DefaultServer.getHostAddress() + ":" + port + "/servletContext/bar", "/servletContext/bar"
                , "");
        runtest("/servletContext/redirect/foo/?redirect=../../bar", "null", "/bar",
                "http://" + DefaultServer.getHostAddress() + ":" + port + "/servletContext/bar", "/servletContext/bar"
                , "");
    }

    private void runtest(String request, String... expectedBody) throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + request);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);

            Assert.assertArrayEquals(expectedBody, split(response));
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
