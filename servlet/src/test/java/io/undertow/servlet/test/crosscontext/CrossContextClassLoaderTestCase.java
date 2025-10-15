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

package io.undertow.servlet.test.crosscontext;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
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

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class CrossContextClassLoaderTestCase {


    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("includer", IncludeServlet.class)
                .addMapping("/a");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(new TempClassLoader("IncluderClassLoader"))
                .setContextPath("/includer")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("includer.war")
                .addServlet(s);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());


        s = new ServletInfo("included", IncludedServlet.class)
                .addMapping("/a");

        builder = new DeploymentInfo()
                .setClassLoader(new TempClassLoader("IncludedClassLoader"))
                .setContextPath("/included")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("included.war")
                .addServlet(s);

        manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testCrossContextRequest() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/includer/a");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(
                    "Including Servlet Class Loader: IncluderClassLoader\r\n" +
                            "Including Servlet Context Path: /includer\r\n" +
                            "Included Servlet Class Loader: IncludedClassLoader\r\n" +
                            "Including Servlet Context Path: /included\r\n",
                    response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    private static final class IncludeServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.getWriter().println("Including Servlet Class Loader: " + Thread.currentThread().getContextClassLoader().toString());
            resp.getWriter().println("Including Servlet Context Path: " + req.getServletContext().getContextPath());
            ServletContext context = req.getServletContext().getContext("/included");
            context.getRequestDispatcher("/a").include(req, resp);
        }
    }

    private static final class IncludedServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.getWriter().println("Included Servlet Class Loader: " + Thread.currentThread().getContextClassLoader().toString());
            resp.getWriter().println("Including Servlet Context Path: " + req.getServletContext().getContextPath());
        }
    }


    private static final class TempClassLoader extends ClassLoader {
        private final String name;


        private TempClassLoader(String name) {
            super(TempClassLoader.class.getClassLoader());
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
