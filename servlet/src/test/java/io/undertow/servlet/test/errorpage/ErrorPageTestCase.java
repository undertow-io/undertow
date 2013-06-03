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

package io.undertow.servlet.test.errorpage;

import java.io.IOException;

import javax.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
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
        builder1.addErrorPage(new ErrorPage("/404", 404));
        builder1.addErrorPage(new ErrorPage("/500", 500));
        builder1.addErrorPage(new ErrorPage("/parentException", ParentException.class));
        builder1.addErrorPage(new ErrorPage("/childException", ChildException.class));
        builder1.addErrorPage(new ErrorPage("/runtimeException", RuntimeException.class));

        builder1.setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ErrorPageTestCase.class.getClassLoader())
                .setContextPath("/servletContext1")
                .setDeploymentName("servletContext1.war");

        final DeploymentManager manager1 = container.addDeployment(builder1);
        manager1.deploy();
        root.addPath(builder1.getContextPath(), manager1.start());


        DeploymentInfo builder2 = new DeploymentInfo();

        builder2.addServlet(new ServletInfo("error", ErrorServlet.class)
                .addMapping("/error"));

        builder2.addServlet(new ServletInfo("path", PathServlet.class)
                .addMapping("/*"));

        builder2.addErrorPage(new ErrorPage("/404", 404));
        builder2.addErrorPage(new ErrorPage("/501", 501));
        builder2.addErrorPage(new ErrorPage("/parentException", ParentException.class));
        builder2.addErrorPage(new ErrorPage("/childException", ChildException.class));
        builder2.addErrorPage(new ErrorPage("/runtimeException", RuntimeException.class));

        builder2.setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ErrorPageTestCase.class.getClassLoader())
                .setContextPath("/servletContext2")
                .setDeploymentName("servletContext2.war");

        final DeploymentManager manager2 = container.addDeployment(builder2);
        manager2.deploy();
        root.addPath(builder2.getContextPath(), manager2.start());

    }


    @Test
    public void testErrorPages() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            runTest(1, client, 404, null, "/404");
            runTest(1, client, 500, null, "/500");
            runTest(1, client, 501, null, "/defaultErrorPage");
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
            runTest(2, client, 404, null, "/404");
            runTest(2, client, 501, null, "/501");
            runTest(2, client, 500, null, "<html><head><title>Error</title></head><body>Internal Server Error</body></html>");
            runTest(2, client, null, ParentException.class, "/parentException");
            runTest(2, client, null, ChildException.class, "/childException");
            runTest(2, client, null, RuntimeException.class, "/runtimeException");
            runTest(2, client, null, IllegalStateException.class, "/runtimeException");
            runTest(2, client, null, Exception.class, "<html><head><title>Error</title></head><body>Internal Server Error</body></html>");
            runTest(2, client, null, IOException.class, "<html><head><title>Error</title></head><body>Internal Server Error</body></html>");
            runTest(2, client, null, ServletException.class, "<html><head><title>Error</title></head><body>Internal Server Error</body></html>");
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void runTest(int deploymentNo, final TestHttpClient client, Integer statusCode, Class<?> exception, String expected) throws IOException {
        final HttpGet get;
        final HttpResponse result;
        final String response;
        get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext" + deploymentNo + "/error?" + (statusCode != null ? "statusCode=" + statusCode : "exception=" + exception.getName()));
        result = client.execute(get);
        Assert.assertEquals(statusCode == null ? 500 : statusCode, result.getStatusLine().getStatusCode());
        response = HttpClientUtils.readResponse(result);
        Assert.assertEquals(expected, response);
    }
}
