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

package io.undertow.servlet.test.streams;

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
import io.undertow.util.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletOutputStreamTestCase {

    public static String message;

    public static final String HELLO_WORLD = "Hello World";
    public static final String BLOCKING_SERVLET = "blockingOutput";
    public static final String ASYNC_SERVLET = "asyncOutput";

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s1 = new ServletInfo(BLOCKING_SERVLET, BlockingOutputStreamServlet.class)
                .addMapping("/" + BLOCKING_SERVLET);

        ServletInfo s2 = new ServletInfo(ASYNC_SERVLET, AsyncOutputStreamServlet.class)
                .addMapping("/" + ASYNC_SERVLET)
                .setAsyncSupported(true);

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(ServletOutputStreamTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER)
                .addServlets(s1, s2);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testBlockingServletOutputStream() {
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 1000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, BLOCKING_SERVLET, false, false, 1);
                runTest(message, BLOCKING_SERVLET, true, false, 10);
                runTest(message, BLOCKING_SERVLET, false, true, 3);
                runTest(message, BLOCKING_SERVLET, true, true, 7);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletOutputStream() {
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, ASYNC_SERVLET, false, false, 1);
                runTest(message, ASYNC_SERVLET, true, false, 10);
                runTest(message, ASYNC_SERVLET, false, true, 3);
                runTest(message, ASYNC_SERVLET, true, true, 7);
            } catch (Exception e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    public void runTest(final String message, String url, final boolean flush, final boolean close, int reps) throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            ServletOutputStreamTestCase.message = message;
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext/" + url + "?reps=" + reps + "&";
            if (flush) {
                uri = uri + "flush=true&";
            }
            if (close) {
                uri = uri + "close=true&";
            }
            HttpGet get = new HttpGet(uri);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            StringBuilder builder = new StringBuilder(reps * message.length());
            for (int j = 0; j < reps; ++j) {
                builder.append(message);
            }
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(builder.toString(), response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
