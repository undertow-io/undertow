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

import javax.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.HttpClientUtils;
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
public class ServletPathMappingTestCase {


    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo aStar = new ServletInfo("/a/*", PathMappingServlet.class)
                .addMapping("/a/*");

        ServletInfo aa = new ServletInfo("/aa", PathMappingServlet.class)
                .addMapping("/aa");

        ServletInfo aaStar = new ServletInfo("/aa/*", PathMappingServlet.class)
                .addMapping("/aa/*");

        ServletInfo ab = new ServletInfo("/a/b/*", PathMappingServlet.class)
                .addMapping("/a/b/*");

        ServletInfo d = new ServletInfo("/", PathMappingServlet.class)
                .addMapping("/");

        ServletInfo jsp = new ServletInfo("*.jsp", PathMappingServlet.class)
                .addMapping("*.jsp");

        ServletInfo cr = new ServletInfo("contextRoot", PathMappingServlet.class)
                .addMapping("");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ServletPathMappingTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER)
                .addServlets(aStar, aa, aaStar, ab, d, cr, jsp);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPath(builder.getContextPath(), manager.start());

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

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/a/c");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/a/*", response);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/aa/b");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/aa/*", response);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/a/b/c/d");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/a/b/*", response);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/a/b");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/a/b/*", response);


            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/defaultStuff");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("/", response);


            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("contextRoot", response);

            get = new HttpGet(DefaultServer.getDefaultServerAddress() + "/servletContext/bob.jsp");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("*.jsp", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
