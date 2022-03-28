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

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletStackTraces;
import io.undertow.servlet.test.security.constraint.ServletIdentityManager;
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

import jakarta.servlet.ServletException;
import java.io.IOException;
import jakarta.servlet.RequestDispatcher;
import org.hamcrest.CoreMatchers;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SecurityErrorPageTestCase {


    @BeforeClass
    public static void setup() throws IOException, ServletException {

        final ServletContainer container = ServletContainer.Factory.newInstance();
        final PathHandler root = new PathHandler();
        DefaultServer.setRootHandler(root);

        DeploymentInfo builder = new DeploymentInfo();

        builder.addServlet(new ServletInfo("secure", SecureServlet.class)
                .addMapping("/secure"))
                .addSecurityConstraint(Servlets.securityConstraint().addRoleAllowed("user").addWebResourceCollection(Servlets.webResourceCollection().addUrlPattern("/*")));

        builder.addServlet(new ServletInfo("path", PathServlet.class)
                .addMapping("/*"));

        builder.addErrorPage(new ErrorPage("/401", StatusCodes.UNAUTHORIZED));

        ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user1", "password1"); // Just one role less user.

        builder.setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ErrorPageTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setServletStackTraces(ServletStackTraces.NONE)
                .setIdentityManager(identityManager)
                .setLoginConfig(Servlets.loginConfig("BASIC", "Test Realm"))
                .setDeploymentName("servletContext.war");

        final DeploymentManager manager1 = container.addDeployment(builder);
        manager1.deploy();
        root.addPrefixPath(builder.getContextPath(), manager1.start());

    }


    @Test
    public void testErrorPages() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            runTest(client, StatusCodes.UNAUTHORIZED, "/401");
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void runTest(final TestHttpClient client, int statusCode, String expected) throws IOException {
        final HttpGet get;
        final HttpResponse result;
        final String response;
        get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/secure");
        result = client.execute(get);
        Assert.assertEquals(statusCode, result.getStatusLine().getStatusCode());
        response = HttpClientUtils.readResponse(result);
        Assert.assertThat(response, CoreMatchers.startsWith(expected));
        // check error attributes
        Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_REQUEST_URI + "=/servletContext/secure"));
        Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_SERVLET_NAME + "=secure"));
        Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_MESSAGE + "=" + StatusCodes.getReason(statusCode)));
        Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.ERROR_STATUS_CODE + "=" + statusCode));
        // check forward attributes
        Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.FORWARD_REQUEST_URI + "=/servletContext/secure"));
        Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.FORWARD_CONTEXT_PATH + "=/servletContext"));
        // RequestDispatcher.FORWARD_QUERY_STRING is null
        // RequestDispatcher.FORWARD_PATH_INFO is null
        Assert.assertThat(response, CoreMatchers.containsString(RequestDispatcher.FORWARD_SERVLET_PATH + "=/secure"));
    }
}
