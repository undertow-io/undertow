/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.undertow.servlet.Servlets.servlet;

/**
 * Tests that asynchronous processing a dispatch started is completed once an error page has answered an error the
 * dispatch sent, and that its listeners are told only after the error page has run.
 */
@RunWith(DefaultServer.class)
public class AsyncSendErrorCompletionTestCase {

    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @BeforeClass
    public static void setup() {
        DeploymentUtils.setupServlet(
                (DeploymentInfo deploymentInfo, ServletContext servletContext) ->
                        deploymentInfo.addErrorPages(new ErrorPage("/error-page", 503)),
                servlet("sendsError", SendsErrorServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/sends-error"),
                servlet("errorPage", ErrorPageServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/error-page"),
                servlet("dispatches", DispatchesServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/dispatches"));
    }

    @Before
    public void clearEvents() {
        EVENTS.clear();
    }

    @Test
    public void testProcessingCompletesOnceTheErrorPageHasAnsweredTheError() throws Exception {
        Assert.assertEquals("error page", get("/sends-error"));
        assertEventsBecome("errorPage", "onComplete");
    }

    @Test
    public void testListenersHearTheCompletionTheDispatchRequestedAfterTheErrorPage() throws Exception {
        Assert.assertEquals("error page", get("/sends-error?complete=true"));
        assertEventsBecome("errorPage", "onComplete");
    }

    @Test
    public void testProcessingAnAsyncDispatchStartedAgainCompletesOnceTheErrorPageHasAnsweredTheError() throws Exception {
        Assert.assertEquals("error page", get("/dispatches"));
        assertEventsBecome("errorPage", "onComplete");
    }

    private static String get(String path) throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext" + path));
            Assert.assertEquals(503, result.getStatusLine().getStatusCode());
            return HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private static void assertEventsBecome(String... expected) throws InterruptedException {
        List<String> expectedEvents = Arrays.asList(expected);
        long deadline = System.currentTimeMillis() + 5000;
        while (!EVENTS.equals(expectedEvents) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        Thread.sleep(200);
        Assert.assertEquals(expectedEvents, EVENTS);
    }

    public static class SendsErrorServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            req.startAsync().addListener(new AsyncListener() {

                @Override
                public void onComplete(AsyncEvent event) {
                    EVENTS.add("onComplete");
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                    EVENTS.add("onTimeout");
                }

                @Override
                public void onError(AsyncEvent event) {
                    EVENTS.add("onError");
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                    EVENTS.add("onStartAsync");
                }
            });
            resp.sendError(503);
            if (Boolean.parseBoolean(req.getParameter("complete"))) {
                req.getAsyncContext().complete();
            }
        }
    }

    public static class DispatchesServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
            req.startAsync().dispatch("/sends-error");
        }
    }

    public static class ErrorPageServlet extends HttpServlet {

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            EVENTS.add("errorPage");
            resp.getWriter().write("error page");
        }
    }
}
