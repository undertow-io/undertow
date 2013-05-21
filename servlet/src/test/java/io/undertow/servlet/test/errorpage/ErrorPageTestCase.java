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

        DeploymentInfo builder = new DeploymentInfo();

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        builder.addServlet(new ServletInfo("error", ErrorServlet.class)
                .addMapping("/error"));

        builder.addServlet(new ServletInfo("path", PathServlet.class)
                .addMapping("/*"));

        builder.addErrorPage(new ErrorPage("/defaultErrorPage"));
        builder.addErrorPage(new ErrorPage("/404", 404));
        builder.addErrorPage(new ErrorPage("/500", 500));
        builder.addErrorPage(new ErrorPage("/parentException", ParentException.class));
        builder.addErrorPage(new ErrorPage("/childException", ChildException.class));
        builder.addErrorPage(new ErrorPage("/runtimeException", RuntimeException.class));

        builder.setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ErrorPageTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war");

        final DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }


    @Test
    public void testErrorPages() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            runTest(client, 404, null, "/404");
            runTest(client, 500, null, "/500");
            runTest(client, 501, null, "/defaultErrorPage");
            runTest(client, null, ParentException.class, "/parentException");
            runTest(client, null, ChildException.class, "/childException");
            runTest(client, null, RuntimeException.class, "/runtimeException");
            runTest(client, null, IllegalStateException.class, "/runtimeException");
            runTest(client, null, Exception.class, "/defaultErrorPage");
            runTest(client, null, IOException.class, "/defaultErrorPage");
            runTest(client, null, ServletException.class, "/defaultErrorPage");
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void runTest(final TestHttpClient client, Integer statusCode, Class<?> exception, String expected) throws IOException {
        final HttpGet get;
        final HttpResponse result;
        final String response;
        get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/error?" + (statusCode != null ? "statusCode=" + statusCode : "exception=" + exception.getName()));
        result = client.execute(get);
        Assert.assertEquals(statusCode == null ? 500 : statusCode, result.getStatusLine().getStatusCode());
        response = HttpClientUtils.readResponse(result);
        Assert.assertEquals(expected, response);
    }
}
