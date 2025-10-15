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

package io.undertow.servlet.test.listener.request.async.onError;

import java.io.IOException;

import jakarta.servlet.ServletException;

import io.undertow.testutils.ProxyIgnore;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoggingExceptionHandler;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

/**
 * @author Jozef Hartinger
 * @see https://issues.jboss.org/browse/UNDERTOW-30
 * @see https://issues.jboss.org/browse/UNDERTOW-31
 * @see https://issues.jboss.org/browse/UNDERTOW-32
 */
@RunWith(DefaultServer.class)
public class AsyncListenerOnErrorTest {

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo f = new ServletInfo("faultyServlet", FaultyServlet.class)
                .addMapping("/faulty");


        ServletInfo a1 = new ServletInfo("asyncServlet1", AsyncServlet1.class)
                .setAsyncSupported(true)
                .addMapping("/async1");

        ServletInfo a2 = new ServletInfo("asyncServlet2", AsyncServlet2.class)
                .setAsyncSupported(true)
                .addMapping("/async2");


        ServletInfo a3 = new ServletInfo("asyncServlet3", AsyncServlet3.class)
                .setAsyncSupported(true)
                .addMapping("/async3");

        ServletInfo a4 = new ServletInfo("asyncServlet4", AsyncServlet4.class)
                .setAsyncSupported(true)
                .addMapping("/async4");

        ServletInfo a5 = new ServletInfo("asyncServlet5", AsyncServlet5.class)
                .setAsyncSupported(true)
                .addMapping("/async5");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(AsyncListenerOnErrorTest.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlets(f, a1, a2, a3, a4, a5);

        builder.setExceptionHandler(LoggingExceptionHandler.builder()
                .add(IllegalStateException.class, "io.undertow", Logger.Level.DEBUG)
                .build());


        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testAsyncListenerOnErrorInvoked1() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/async1");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(SimpleAsyncListener.MESSAGE, response);
            Assert.assertArrayEquals(new String[]{"ERROR", "COMPLETE"}, AsyncEventListener.results(2));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testAsyncListenerOnErrorInvoked2() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/async2");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(SimpleAsyncListener.MESSAGE, response);
            Assert.assertArrayEquals(new String[]{"ERROR", "COMPLETE"}, AsyncEventListener.results(2));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testMultiAsyncDispatchError() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/async3");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(SimpleAsyncListener.MESSAGE, response);
            Assert.assertArrayEquals(new String[]{"START", "ERROR", "COMPLETE"}, AsyncEventListener.results(3));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    /**
     * Regression test for UNDERTOW-1455
     *
     * Compared to testAsyncListenerOnErrorInvoked* tests, exception is thrown in
     * entering servlet not in asynchronous dispatch part.
     */
    @Test
    public void testAsyncListenerOnErrorExceptionInFirstServlet() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/async4");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(SimpleAsyncListener.MESSAGE, response);
            Assert.assertArrayEquals(new String[]{"ERROR", "COMPLETE"}, AsyncEventListener.results(2));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test @ProxyIgnore
    // FIXME UNDERTOW-1523
    public void testAsyncErrorOnClientBreakdown() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/async5");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            org.apache.http.client.utils.HttpClientUtils.closeQuietly(client);

            Assert.assertArrayEquals(new String[]{"ERROR", "COMPLETE"}, AsyncEventListener.results(3));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
