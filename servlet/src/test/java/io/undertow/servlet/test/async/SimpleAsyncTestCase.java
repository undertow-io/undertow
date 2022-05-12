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

package io.undertow.servlet.test.async;

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.api.ServletStackTraces;
import io.undertow.servlet.api.ThreadSetupHandler;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static io.undertow.servlet.Servlets.servlet;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SimpleAsyncTestCase {

    public static final String HELLO_WORLD = "Hello World";

    private static class SimpleDateThreadSetupHandler implements ThreadSetupHandler {
        @Override
        public <T, C> Action<T, C> create(Action<T, C> action) {
            return (exchange, context) -> {
                SimpleDateThreadLocalAsyncServlet.initThreadLocalSimpleDate();
                try {
                    return action.call(exchange, context);
                } finally {
                    SimpleDateThreadLocalAsyncServlet.removeThreadLocalSimpleDate();
                }
            };
        }
    };

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(new ServletExtension() {
            @Override
            public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
                deploymentInfo.setServletStackTraces(ServletStackTraces.NONE);
                deploymentInfo.addErrorPages(new ErrorPage("/500", StatusCodes.INTERNAL_SERVER_ERROR));
                deploymentInfo.addThreadSetupAction(new SimpleDateThreadSetupHandler());
            }
        },
                servlet("messageServlet", MessageServlet.class)
                        .addInitParam(MessageServlet.MESSAGE, HELLO_WORLD)
                        .setAsyncSupported(true)
                        .addMapping("/message"),
                servlet("500", MessageServlet.class)
                        .addInitParam(MessageServlet.MESSAGE, "500")
                        .setAsyncSupported(true)
                        .addMapping("/500"),
                servlet("asyncServlet", AsyncServlet.class)
                        .addInitParam(MessageServlet.MESSAGE, HELLO_WORLD)
                        .setAsyncSupported(true)
                        .addMapping("/async"),
                servlet("asyncServlet2", AnotherAsyncServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/async2"),
                servlet("error", AsyncErrorServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/error"),
                servlet("errorlistener", AsyncErrorListenerServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/errorlistener"),
                servlet("dispatch", AsyncDispatchServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/dispatch"),
                servlet("doubleCompleteServlet", AsyncDoubleCompleteServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/double-complete"),
                servlet("simpleDateThreadLocal", SimpleDateThreadLocalAsyncServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/simple-date-thread-local"));

    }

    @Test
    public void testSimpleHttpServlet() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/async");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(HELLO_WORLD, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSimpleHttpAsyncServletWithoutDispatch() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/async2");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(AnotherAsyncServlet.class.getSimpleName(), response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testErrorServlet() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/error");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("500", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testErrorListenerServlet() throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/errorlistener");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("500", response);
            Assert.assertEquals("ERROR", AsyncErrorListenerServlet.EVENTS.poll(10, TimeUnit.SECONDS));
            Assert.assertEquals("COMPLETED", AsyncErrorListenerServlet.EVENTS.poll(10, TimeUnit.SECONDS));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
    @Test
    public void testWrappedDispatch() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/dispatch");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("wrapped: " + HELLO_WORLD, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
    @Test
    public void testErrorServletWithPostData() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/error");
            post.setEntity(new StringEntity("Post body stuff"));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("500", response);

            post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/error");
            post.setEntity(new StringEntity("Post body stuff"));
            result = client.execute(post);
            Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("500", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testServletCompletesTwiceOnInitialThread() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/double-complete");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(HELLO_WORLD, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSimpleDateThreahLocalAsyncServlet() throws IOException, ParseException {
        TestHttpClient client = new TestHttpClient();
        try {
            final Date start = new Date();
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/simple-date-thread-local");
            HttpResponse result = client.execute(get);
            Assert.assertEquals("Response status is not OK", StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertNotEquals("Date thread-local was not found", SimpleDateThreadLocalAsyncServlet.NULL_THREAD_LOCAL, response);
            final Date date = SimpleDateThreadLocalAsyncServlet.parseDate(response);
            Assert.assertTrue("Date thread-local is not in range", date.compareTo(start) >= 0 && date.compareTo(new Date()) <= 0);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
