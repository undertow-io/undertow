/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.defaultservlet;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.LOCATION;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import jakarta.servlet.ServletException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.test.path.ServletPathMappingTestCase;
import io.undertow.servlet.test.security.constraint.ServletIdentityManager;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.FlexBase64;
import io.undertow.util.StatusCodes;

/**
 * TestCase on redirect with or without trailing slash when requesting protected path.
 *
 * @author Lin Gao
 */
@RunWith(DefaultServer.class)
public class SecurityRedirectTestCase {

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user1", "password1", "role1");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ServletPathMappingTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setResourceManager(new TestResourceLoader(SecurityRedirectTestCase.class))
                .addWelcomePages("index.html")
                .setIdentityManager(identityManager)
                .setLoginConfig(new LoginConfig("BASIC", "Test Realm"))
                .addSecurityConstraint(new SecurityConstraint()
                        .addRoleAllowed("role1")
                        .addWebResourceCollection(new WebResourceCollection()
                                .addUrlPatterns("/index.html", "/filterpath/*")));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(root);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSecurityWithWelcomeFileRedirect() throws IOException {
        // disable following redirect
        HttpClient client = HttpClientBuilder.create().disableRedirectHandling().build();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.FOUND, result.getStatusLine().getStatusCode());
            Header[] values = result.getHeaders(LOCATION.toString());
            assertEquals(1, values.length);
            assertEquals(DefaultServer.getDefaultServerURL() + "/servletContext/", values[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());

            values = result.getHeaders(WWW_AUTHENTICATE.toString());
            assertEquals(1, values.length);
            assertEquals(BASIC + " realm=\"Test Realm\"", values[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/");
            get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("user1:password1".getBytes(), false));
            result = client.execute(get);
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertTrue(response.contains("Redirected home page"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSecurityWithoutWelcomeFileRedirect() throws IOException {
        // disable following redirect
        HttpClient client = HttpClientBuilder.create().disableRedirectHandling().build();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/filterpath");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/filterpath/");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());

            Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
            assertEquals(1, values.length);
            assertEquals(BASIC + " realm=\"Test Realm\"", values[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/filterpath");
            get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("user1:password1".getBytes(), false));
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.FOUND, result.getStatusLine().getStatusCode());
            values = result.getHeaders(LOCATION.toString());
            assertEquals(1, values.length);
            assertEquals(DefaultServer.getDefaultServerURL() + "/servletContext/filterpath/", values[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/filterpath/filtered.txt");
            get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("user1:password1".getBytes(), false));
            result = client.execute(get);
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertTrue(response.equals("Stuart"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
