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

package io.undertow.servlet.test.session;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSessionConfig;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import io.undertow.testutils.TestHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * Test that separate servlet deployments use seperate session managers, even in the presence of forwards,
 * and that sessions created in a forwarded context are accessible to later direct requests
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class CrossContextServletSessionTestCase {


    @BeforeClass
    public static void setup() throws ServletException {

        final ServletContainer container = ServletContainer.Factory.newInstance();
        final PathHandler path = new PathHandler();
        DefaultServer.setRootHandler(path);

        createDeployment("1", container, path);
        createDeployment("2", container, path);

    }

    private static void createDeployment(final String name, final ServletContainer container,  final PathHandler path) throws ServletException {

        ServletInfo s = new ServletInfo("servlet", SessionServlet.class)
                .addMapping("/servlet");
        ServletInfo forward = new ServletInfo("forward", ForwardServlet.class)
                .addMapping("/forward");
        ServletInfo include = new ServletInfo("include", IncludeServlet.class)
                .addMapping("/include");

        ServletInfo includeAdd = new ServletInfo("includeadd", IncludeAddServlet.class)
                .addMapping("/includeadd");
        ServletInfo forwardAdd = new ServletInfo("forwardadd", ForwardAddServlet.class)
                .addMapping("/forwardadd");

        ServletInfo accessTimeServlet = new ServletInfo("accesstimeservlet", LastAccessTimeSessionServlet.class)
                .addMapping("/accesstimeservlet");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/" + name)
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName( name + ".war")
                .setServletSessionConfig(new ServletSessionConfig().setPath("/"))
                .addServlets(s, forward, include, forwardAdd, includeAdd, accessTimeServlet);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        path.addPrefixPath(builder.getContextPath(), manager.start());
    }


    @Test
    public void testSharedSessionCookieMultipleDeployments() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet direct1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/servlet");
            HttpGet direct2 = new HttpGet(DefaultServer.getDefaultServerURL() + "/2/servlet");
            HttpResponse result = client.execute(direct1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);

            result = client.execute(direct1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("2", response);

            result = client.execute(direct2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);

            result = client.execute(direct2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("2", response);

            result = client.execute(direct1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);

            result = client.execute(direct2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testCrossContextSessionForwardInvocation() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet direct1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/servlet");
            HttpGet forward1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/forward?context=/2&path=/servlet");
            HttpGet direct2 = new HttpGet(DefaultServer.getDefaultServerURL() + "/2/servlet");
            HttpGet forward2 = new HttpGet(DefaultServer.getDefaultServerURL() + "/2/forward?context=/1&path=/servlet");
            HttpResponse result = client.execute(direct1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);

            result = client.execute(direct1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("2", response);

            result = client.execute(forward2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);

            result = client.execute(forward2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("4", response);

            result = client.execute(forward1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);

            result = client.execute(forward1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("2", response);

            result = client.execute(direct2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);

            result = client.execute(direct2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("4", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testCrossContextSessionForwardAccessTimeInvocation() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet direct1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/accesstimeservlet");
            HttpGet forward1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/forward?context=/2&path=/accesstimeservlet");

            HttpResponse result = client.execute(direct1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.startsWith("1 "));

            result = client.execute(forward1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.startsWith("1 "));

            Thread.sleep(50);
            result = client.execute(forward1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.startsWith("2 "));
            Long time1 = Long.parseLong(response.substring(2));

            Thread.sleep(50);
            result = client.execute(forward1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.startsWith("3 "));
            Long time2 = Long.parseLong(response.substring(2));
            Assert.assertTrue(time2 > time1); // access time updated in forward app

            result = client.execute(direct1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.startsWith("2 "));
            Long time3 = Long.parseLong(response.substring(2));
            Assert.assertTrue(time3 > time2); // access time updated in outer app

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testCrossContextSessionForwardInvocationWithBothServletsAdding() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet direct1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/servlet");
            HttpGet forward1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/forwardadd?context=/2&path=/servlet");
            HttpGet direct2 = new HttpGet(DefaultServer.getDefaultServerURL() + "/2/servlet");
            HttpGet forward2 = new HttpGet(DefaultServer.getDefaultServerURL() + "/2/forwardadd?context=/1&path=/servlet");
            HttpResponse result = client.execute(forward1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);

            result = client.execute(direct1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("2", response);

            result = client.execute(forward2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);

            result = client.execute(forward2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("4", response);

            result = client.execute(forward1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("4", response);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }
    @Test
    public void testCrossContextSessionIncludeInvocation() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet direct1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/servlet");
            HttpGet include1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/include?context=/2&path=/servlet");
            HttpGet direct2 = new HttpGet(DefaultServer.getDefaultServerURL() + "/2/servlet");
            HttpGet include2 = new HttpGet(DefaultServer.getDefaultServerURL() + "/2/include?context=/1&path=/servlet");
            HttpResponse result = client.execute(include2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);

            result = client.execute(direct1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("2", response);

            result = client.execute(include2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);

            result = client.execute(include2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("4", response);

            result = client.execute(include1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);

            result = client.execute(include1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("2", response);

            result = client.execute(direct2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);

            result = client.execute(direct2);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("4", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testCrossContextSessionIncludeAccessTimeInvocation() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet direct1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/accesstimeservlet");
            HttpGet include1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/include?context=/2&path=/accesstimeservlet");

            HttpResponse result = client.execute(direct1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.startsWith("1 "));

            result = client.execute(include1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.startsWith("1 "));

            Thread.sleep(50);
            result = client.execute(include1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.startsWith("2 "));
            Long time1 = Long.parseLong(response.substring(2));

            Thread.sleep(50);
            result = client.execute(include1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.startsWith("3 "));
            Long time2 = Long.parseLong(response.substring(2));
            Assert.assertTrue(time2 > time1); // access time updated in include app

            result = client.execute(direct1);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.startsWith("2 "));
            Long time3 = Long.parseLong(response.substring(2));
            Assert.assertTrue(time3 > time2); // access time updated in outer app

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    public static class ForwardServlet extends HttpServlet {

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
            req.getServletContext().getContext(req.getParameter("context")).getRequestDispatcher(req.getParameter("path")).forward(req, resp);
        }
    }


    public static class IncludeServlet extends HttpServlet {

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
            req.getServletContext().getContext(req.getParameter("context")).getRequestDispatcher(req.getParameter("path")).include(req, resp);
        }
    }

    public static class ForwardAddServlet extends HttpServlet {

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
            HttpSession session = req.getSession();
            Integer value = (Integer)session.getAttribute("key");
            if(value == null) {
                value = 1;
            }
            session.setAttribute("key", value + 1);
            req.getServletContext().getContext(req.getParameter("context")).getRequestDispatcher(req.getParameter("path")).forward(req, resp);
        }
    }


    public static class IncludeAddServlet extends HttpServlet {

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
            HttpSession session = req.getSession();
            Integer value = (Integer)session.getAttribute("key");
            if(value == null) {
                value = 1;
            }
            session.setAttribute("key", value + 1);
            req.getServletContext().getContext(req.getParameter("context")).getRequestDispatcher(req.getParameter("path")).include(req, resp);
        }
    }
}
