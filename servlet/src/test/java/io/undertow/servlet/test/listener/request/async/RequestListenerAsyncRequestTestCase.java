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

package io.undertow.servlet.test.listener.request.async;

import java.io.IOException;

import jakarta.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestListener;
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
public class RequestListenerAsyncRequestTestCase {

    public static final String HELLO_WORLD = "Hello World";

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo m = new ServletInfo("messageServlet", MessageServlet.class)
                .addInitParam(MessageServlet.MESSAGE, HELLO_WORLD)
                .setAsyncSupported(true)
                .addMapping("/message");


        ServletInfo a = new ServletInfo("asyncServlet", AsyncServlet.class)
                .addInitParam(MessageServlet.MESSAGE, HELLO_WORLD)
                .setAsyncSupported(true)
                .addMapping("/async");
        ServletInfo comp = new ServletInfo("completeAsyncServlet", CompleteAsyncServlet.class)
                .addInitParam(MessageServlet.MESSAGE, HELLO_WORLD)
                .setAsyncSupported(true)
                .addMapping("/asynccomplete");

        ServletInfo a2 = new ServletInfo("asyncServlet2", AnotherAsyncServlet.class)
        .setAsyncSupported(true)
        .addMapping("/async2");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlets(m, a, a2, comp)
                .addListener(new ListenerInfo(TestListener.class));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testSimpleHttpServlet() throws IOException {
        TestListener.init(4);
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/async");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(HELLO_WORLD, response);

            Assert.assertArrayEquals(new String[]{"created REQUEST", "destroyed REQUEST", "created ASYNC", "destroyed ASYNC"}, TestListener.results().toArray());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }
    @Test
    public void testSimpleHttpServletCompleteInInitialRequest() throws IOException {
        TestListener.init(3);
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/asynccomplete");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("asynccomplete", response);

            Assert.assertArrayEquals(new String[]{"created REQUEST", "onComplete", "destroyed REQUEST"}, TestListener.results().toArray());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSimpleAsyncHttpServletWithoutDispatch() throws IOException {
        TestListener.init(2);
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/async2");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(AnotherAsyncServlet.class.getSimpleName(), response);
            Assert.assertArrayEquals(new String[]{"created REQUEST", "destroyed REQUEST", "created REQUEST", "destroyed REQUEST"}, TestListener.results().toArray());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
