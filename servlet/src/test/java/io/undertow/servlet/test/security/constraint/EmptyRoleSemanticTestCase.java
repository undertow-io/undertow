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
package io.undertow.servlet.test.security.constraint;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.SecurityInfo.EmptyRoleSemantic;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.FlexBase64;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

import jakarta.servlet.ServletException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A test case for the three supported {@link EmptyRoleSemantic} values.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(DefaultServer.class)
public class EmptyRoleSemanticTestCase {

    public static final String HELLO_WORLD = "Hello World";

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", AuthenticationMessageServlet.class)
                .addInitParam(MessageServlet.MESSAGE, HELLO_WORLD)
                .addMapping("/permit")
                .addMapping("/deny")
                .addMapping("/authenticate");

        ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user1", "password1"); // Just one role less user.

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setIdentityManager(identityManager)
                .setLoginConfig(new LoginConfig("BASIC", "Test Realm"))
                .addServlet(s);

        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection().addUrlPattern("/permit"))
                .setEmptyRoleSemantic(EmptyRoleSemantic.PERMIT));

        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection().addUrlPattern("/deny"))
                .setEmptyRoleSemantic(EmptyRoleSemantic.DENY));

        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection().addUrlPattern("/authenticate"))
                .setEmptyRoleSemantic(EmptyRoleSemantic.AUTHENTICATE));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testPermit() throws Exception {
        TestHttpClient client = new TestHttpClient();
        final String url = DefaultServer.getDefaultServerURL() + "/servletContext/permit";
        try {
            HttpGet initialGet = new HttpGet(url);
            initialGet.addHeader("ExpectedMechanism", "None");
            initialGet.addHeader("ExpectedUser", "None");
            HttpResponse result = client.execute(initialGet);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(HELLO_WORLD, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testDeny() throws Exception {
        TestHttpClient client = new TestHttpClient();
        final String url = DefaultServer.getDefaultServerURL() + "/servletContext/deny";
        try {
            HttpGet initialGet = new HttpGet(url);
            initialGet.addHeader("ExpectedMechanism", "None");
            initialGet.addHeader("ExpectedUser", "None");
            HttpResponse result = client.execute(initialGet);
            assertEquals(StatusCodes.FORBIDDEN, result.getStatusLine().getStatusCode());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testAuthenticate() throws Exception {
        TestHttpClient client = new TestHttpClient();
        final String url = DefaultServer.getDefaultServerURL() + "/servletContext/authenticate";
        try {
            HttpGet get = new HttpGet(url);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
            Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
            assertEquals(1, values.length);
            assertEquals(BASIC + " realm=\"Test Realm\"", values[0].getValue());
            HttpClientUtils.readResponse(result);

            get = new HttpGet(url);
            get.addHeader("ExpectedMechanism", "BASIC");
            get.addHeader("ExpectedUser", "user1");
            get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("user1:password1".getBytes(), false));
            result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(HELLO_WORLD, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
