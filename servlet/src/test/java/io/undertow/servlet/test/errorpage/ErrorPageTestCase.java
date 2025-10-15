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

package io.undertow.servlet.test.errorpage;

import java.io.IOException;

import jakarta.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.api.LoggingExceptionHandler;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletStackTraces;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.RequestDispatcher;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.hamcrest.CoreMatchers;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ErrorPageTestCase {

    @BeforeClass
    public static void setup() throws IOException, ServletException {

        final ServletContainer container = ServletContainer.Factory.newInstance();
        final PathHandler root = new PathHandler();
        DefaultServer.setRootHandler(root);

        DeploymentInfo builder1 = new DeploymentInfo();

        builder1.addServlet(new ServletInfo("error", ErrorServlet.class)
                .addMapping("/error"));

        builder1.addServlet(new ServletInfo("path", PathServlet.class)
                .addMapping("/*"));

        builder1.addErrorPage(new ErrorPage("/defaultErrorPage"));
        builder1.addErrorPage(new ErrorPage("/404", StatusCodes.NOT_FOUND));
        builder1.addErrorPage(new ErrorPage("/parentException", ParentException.class));
        builder1.addErrorPage(new ErrorPage("/childException", ChildException.class));
        builder1.addErrorPage(new ErrorPage("/runtimeException", RuntimeException.class));
        builder1.setExceptionHandler(LoggingExceptionHandler.builder()
                .add(ParentException.class, "io.undertow", Logger.Level.DEBUG)
                .add(ChildException.class, "io.undertow", Logger.Level.DEBUG)
                .add(RuntimeException.class, "io.undertow", Logger.Level.DEBUG)
                .add(ServletException.class, "io.undertow", Logger.Level.DEBUG)
                .build());


        builder1.setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ErrorPageTestCase.class.getClassLoader())
                .setContextPath("/servletContext1")
                .setServletStackTraces(ServletStackTraces.NONE)
                .setDeploymentName("servletContext1.war");

        final DeploymentManager manager1 = container.addDeployment(builder1);
        manager1.deploy();
        root.addPrefixPath(builder1.getContextPath(), manager1.start());


        DeploymentInfo builder2 = new DeploymentInfo();

        builder2.addServlet(new ServletInfo("error", ErrorServlet.class)
                .addMapping("/error"));

        builder2.addServlet(new ServletInfo("path", PathServlet.class)
                .addMapping("/*"));

        builder2.addErrorPage(new ErrorPage("/404", StatusCodes.NOT_FOUND));
        builder2.addErrorPage(new ErrorPage("/501", StatusCodes.NOT_IMPLEMENTED));
        builder2.addErrorPage(new ErrorPage("/parentException", ParentException.class));
        builder2.addErrorPage(new ErrorPage("/childException", ChildException.class));
        builder2.addErrorPage(new ErrorPage("/runtimeException", RuntimeException.class));
        builder2.setExceptionHandler(LoggingExceptionHandler.builder()
                .add(ParentException.class, "io.undertow", Logger.Level.DEBUG)
                .add(ChildException.class, "io.undertow", Logger.Level.DEBUG)
                .add(RuntimeException.class, "io.undertow", Logger.Level.DEBUG)
                .add(ServletException.class, "io.undertow", Logger.Level.DEBUG)
                .build());

        builder2.setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ErrorPageTestCase.class.getClassLoader())
                .setContextPath("/servletContext2")
                .setServletStackTraces(ServletStackTraces.NONE)
                .setDeploymentName("servletContext2.war");

        final DeploymentManager manager2 = container.addDeployment(builder2);
        manager2.deploy();
        root.addPrefixPath(builder2.getContextPath(), manager2.start());


        DeploymentInfo builder3 = new DeploymentInfo();

        builder3.addServlet(new ServletInfo("error", ErrorServlet.class)
                .addMapping("/error"));

        builder3.addServlet(new ServletInfo("path", PathServlet.class)
                .addMapping("/*"));

        builder3.addErrorPage(new ErrorPage("/defaultErrorPage"));
        builder3.addErrorPage(new ErrorPage("/404", StatusCodes.NOT_FOUND));
        builder3.addErrorPage(new ErrorPage("/500", StatusCodes.INTERNAL_SERVER_ERROR));
        builder3.addErrorPage(new ErrorPage("/parentException", ParentException.class));
        builder3.addErrorPage(new ErrorPage("/childException", ChildException.class));
        builder3.addErrorPage(new ErrorPage("/runtimeException", RuntimeException.class));

        builder3.setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ErrorPageTestCase.class.getClassLoader())
                .setContextPath("/servletContext3")
                .setServletStackTraces(ServletStackTraces.NONE)
                .setDeploymentName("servletContext3.war");

        builder3.setExceptionHandler(LoggingExceptionHandler.builder()
                .add(ParentException.class, "io.undertow", Logger.Level.DEBUG)
                .add(ChildException.class, "io.undertow", Logger.Level.DEBUG)
                .add(RuntimeException.class, "io.undertow", Logger.Level.DEBUG)
                .add(ServletException.class, "io.undertow", Logger.Level.DEBUG)
                .build());

        final DeploymentManager manager3 = container.addDeployment(builder3);
        manager3.deploy();
        root.addPrefixPath(builder3.getContextPath(), manager3.start());
    }


    @Test
    public void testErrorPages() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            runTest(1, client, StatusCodes.NOT_FOUND, null, "/404");
            runTest(1, client, StatusCodes.INTERNAL_SERVER_ERROR, null, "/defaultErrorPage");
            runTest(1, client, StatusCodes.NOT_IMPLEMENTED, null, "/defaultErrorPage");
            runTest(1, client, null, ParentException.class, "/parentException");
            runTest(1, client, null, ChildException.class, "/childException");
            runTest(1, client, null, RuntimeException.class, "/runtimeException");
            runTest(1, client, null, IllegalStateException.class, "/runtimeException");
            runTest(1, client, null, Exception.class, "/defaultErrorPage");
            runTest(1, client, null, IOException.class, "/defaultErrorPage");
            runTest(1, client, null, ServletException.class, "/defaultErrorPage");
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testErrorPagesWithNoDefaultErrorPage() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            runTest(2, client, StatusCodes.NOT_FOUND, null, "/404");
            runTest(2, client, StatusCodes.NOT_IMPLEMENTED, null, "/501");
            runTest(2, client, StatusCodes.INTERNAL_SERVER_ERROR, null, "<html><head><title>Error</title></head><body>Internal Server Error</body></html>", false);
            runTest(2, client, null, ParentException.class, "/parentException");
            runTest(2, client, null, ChildException.class, "/childException");
            runTest(2, client, null, RuntimeException.class, "/runtimeException");
            runTest(2, client, null, IllegalStateException.class, "/runtimeException");
            runTest(2, client, null, Exception.class, "<html><head><title>Error</title></head><body>Internal Server Error</body></html>", false);
            runTest(2, client, null, IOException.class, "<html><head><title>Error</title></head><body>Internal Server Error</body></html>", false);
            runTest(2, client, null, ServletException.class, "<html><head><title>Error</title></head><body>Internal Server Error</body></html>", false);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    //see UNDERTOW-249
    @Test
    public void testErrorPagesWith500PageMapped() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            runTest(3, client, StatusCodes.NOT_FOUND, null, "/404");
            runTest(3, client, StatusCodes.INTERNAL_SERVER_ERROR, null, "/500");
            runTest(3, client, StatusCodes.NOT_IMPLEMENTED, null, "/defaultErrorPage");
            runTest(3, client, null, ParentException.class, "/parentException");
            runTest(3, client, null, ChildException.class, "/childException");
            runTest(3, client, null, RuntimeException.class, "/runtimeException");
            runTest(3, client, null, IllegalStateException.class, "/runtimeException");
            runTest(3, client, null, Exception.class, "/500");
            runTest(3, client, null, IOException.class, "/500");
            runTest(3, client, null, ServletException.class, "/500");
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void runTest(int deploymentNo, final TestHttpClient client, Integer statusCode,
            Class<?> exception, String expected) throws IOException {
        this.runTest(deploymentNo, client, statusCode, exception, expected, true);
    }

    private void runTest(int deploymentNo, final TestHttpClient client, Integer statusCode, Class<?> exception,
            String expected, boolean checkAttributes) throws IOException {
        final HttpGet get;
        final HttpResponse result;
        final String response;
        get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext" + deploymentNo + "/error?" + (statusCode != null ? "statusCode=" + statusCode : "exception=" + exception.getName()));
        result = client.execute(get);
        Assert.assertEquals(statusCode == null ? StatusCodes.INTERNAL_SERVER_ERROR : statusCode, result.getStatusLine().getStatusCode());
        response = HttpClientUtils.readResponse(result);
        Assert.assertThat(response, CoreMatchers.startsWith(expected));
        if (checkAttributes) {
            // check error attributes
            Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_REQUEST_URI + "=/servletContext" + deploymentNo + "/error"));
            Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_SERVLET_NAME + "=error"));
            if (statusCode == null) {
                if (RuntimeException.class.isAssignableFrom(exception)) {
                    Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_EXCEPTION_TYPE + "=" + exception));
                    Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_EXCEPTION + "=" + exception.getName()));
                    // RequestDispatcher.ERROR_MESSAGE is null
                    Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_STATUS_CODE + "=500"));
                } else {
                    Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_EXCEPTION_TYPE + "=" + exception));
                    Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_EXCEPTION + "=" + exception.getName()));
                    Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_MESSAGE + "=" + exception.getName()));
                    Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_STATUS_CODE + "=500"));
                }
            } else {
                Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_MESSAGE + "=" + StatusCodes.getReason(statusCode)));
                Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_STATUS_CODE + "=" + statusCode));
            }
            // check forward attributes
            Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.FORWARD_REQUEST_URI + "=/servletContext" + deploymentNo + "/error"));
            Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.FORWARD_CONTEXT_PATH + "=/servletContext" + deploymentNo));
            Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.FORWARD_QUERY_STRING + "=" + (statusCode != null ? "statusCode=" + statusCode : "exception=" + exception.getName())));
            // RequestDispatcher.FORWARD_PATH_INFO is null
            Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.FORWARD_SERVLET_PATH + "=/error"));
        }
    }
}
