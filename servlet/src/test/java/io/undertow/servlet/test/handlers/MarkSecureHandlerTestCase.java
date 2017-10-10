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

package io.undertow.servlet.test.handlers;

import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.SecureCookieHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.handlers.MarkSecureHandler;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class MarkSecureHandlerTestCase {

    public static final String HELLO_WORLD = "Hello World";

    @Test
    public void testMarkSecureHandler() throws IOException, GeneralSecurityException, ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", MessageServlet.class)
                .addInitParam(MessageServlet.MESSAGE, HELLO_WORLD)
                .addMapping("/issecure");
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(MarkSecureHandlerTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlet(s);

        builder.addFilter(new FilterInfo("issecure-filter", IsSecureFilter.class));
        builder.addFilterUrlMapping("issecure-filter", "/*", DispatcherType.REQUEST);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(new MarkSecureHandler(root));

        TestHttpClient client = new TestHttpClient();

        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/issecure");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            // When MarkSecureHandler is enabled, req.isSecure() should be true
            Assert.assertEquals("true", result.getHeaders("issecure")[0].getValue());
            // When SecureCookieHandler is not enabled, secure cookie is not automatically enabled.
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar", header.getValue());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(HELLO_WORLD, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testMarkSecureHandlerWithSecureCookieHandler() throws IOException, GeneralSecurityException, ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", MessageServlet.class)
                .addInitParam(MessageServlet.MESSAGE, HELLO_WORLD)
                .addMapping("/issecure");
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(MarkSecureHandlerTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlet(s);

        builder.addFilter(new FilterInfo("issecure-filter", IsSecureFilter.class));
        builder.addFilterUrlMapping("issecure-filter", "/*", DispatcherType.REQUEST);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(new MarkSecureHandler(new SecureCookieHandler(root)));

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/issecure");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            // When MarkSecureHandler is enabled, req.isSecure() should be true
            Assert.assertEquals("true", result.getHeaders("issecure")[0].getValue());
            // When SecureCookieHandler is enabled with MarkSecureHandler, secure cookie is enabled as this channel is treated as secure
            Header header = result.getFirstHeader("set-cookie");
            Assert.assertEquals("foo=bar; secure", header.getValue());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(HELLO_WORLD, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
