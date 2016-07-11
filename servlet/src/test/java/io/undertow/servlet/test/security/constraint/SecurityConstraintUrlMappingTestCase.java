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

import java.io.IOException;

import javax.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.FlexBase64;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SecurityConstraintUrlMappingTestCase {

    public static final String HELLO_WORLD = "Hello World";

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", AuthenticationMessageServlet.class)
                .addInitParam(MessageServlet.MESSAGE, HELLO_WORLD)
                .addMapping("/role1")
                .addMapping("/role2")
                .addMapping("/starstar")
                .addMapping("/secured/role2/*")
                .addMapping("/secured/1/2/*")
                .addMapping("/public/*")
                .addMapping("/extension/*");

        ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user1", "password1", "role1");
        identityManager.addUser("user2", "password2", "role2", "**");
        identityManager.addUser("user3", "password3", "role1", "role2");
        identityManager.addUser("user4", "password4", "badRole");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setIdentityManager(identityManager)
                .setLoginConfig(new LoginConfig("BASIC", "Test Realm"))
                .addServlet(s);

        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/role1"))
                .addRoleAllowed("role1"));

        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/starstar"))
                .addRoleAllowed("**"));
        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/secured/*"))
                .addRoleAllowed("role2"));
        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/secured/*"))
                .addRoleAllowed("role2"));
        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/secured/1/*"))
                .addRoleAllowed("role1"));
        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/secured/1/2/*"))
                .addRoleAllowed("role2"));
        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("*.html"))
                .addRoleAllowed("role2"));
        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/public/*")).setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.PERMIT));
        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/public/postSecured/*")
                        .addHttpMethod("POST"))
                .addRoleAllowed("role1"));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/star")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setIdentityManager(identityManager)
                .setLoginConfig(new LoginConfig("BASIC", "Test Realm"))
                .addSecurityRole("**")
                .addServlet(s);

        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/starstar"))
                .addRoleAllowed("**"));

        manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testExactMatch() throws IOException {
        runSimpleUrlTest(DefaultServer.getDefaultServerURL() + "/servletContext/role1", "user2:password2", "user1:password1");
    }

    @Test
    public void testPatternMatch() throws IOException {
        runSimpleUrlTest(DefaultServer.getDefaultServerURL() + "/servletContext/secured/role2/aa", "user1:password1", "user2:password2");
    }

    @Test
    public void testStartStar() throws IOException {
        runSimpleUrlTest(DefaultServer.getDefaultServerURL() + "/servletContext/starstar", null, "user2:password2");
    }

    @Test
    public void testStartStar2() throws IOException {
        runSimpleUrlTest(DefaultServer.getDefaultServerURL() + "/star/starstar", "user1:password1", "user2:password2");
    }
    @Test
    public void testExtensionMatch() throws IOException {
        runSimpleUrlTest(DefaultServer.getDefaultServerURL() + "/servletContext/extension/a.html", "user1:password1", "user2:password2");
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/public/a.html");
            get.addHeader("ExpectedMechanism", "None");
            get.addHeader("ExpectedUser", "None");
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testAggregatedRoles() throws IOException {
        runSimpleUrlTest(DefaultServer.getDefaultServerURL() + "/servletContext/secured/1/2/aa", "user4:password4", "user3:password3");
        runSimpleUrlTest(DefaultServer.getDefaultServerURL() + "/servletContext/secured/1/2/aa", "user1:password1", "user2:password2");
    }

    @Test
    public void testHttpMethod() throws IOException {
        TestHttpClient client = new TestHttpClient();
        final String url = DefaultServer.getDefaultServerURL() + "/servletContext/public/postSecured/a";
        try {
            HttpGet initialGet = new HttpGet(url);
            initialGet.addHeader("ExpectedMechanism", "None");
            initialGet.addHeader("ExpectedUser", "None");
            HttpResponse result = client.execute(initialGet);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            HttpPost post = new HttpPost(url);
            result = client.execute(post);
            assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
            Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
            assertEquals(1, values.length);
            assertEquals(BASIC + " realm=\"Test Realm\"", values[0].getValue());
            HttpClientUtils.readResponse(result);

            post = new HttpPost(url);
            post.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("user2:password2".getBytes(), false));
            result = client.execute(post);
            assertEquals(StatusCodes.FORBIDDEN, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            post = new HttpPost(url);
            post.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("user1:password1".getBytes(), false));
            post.addHeader("ExpectedMechanism", "BASIC");
            post.addHeader("ExpectedUser", "user1");
            result = client.execute(post);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(HELLO_WORLD, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    public void runSimpleUrlTest(final String url, final String badUser, final String goodUser) throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(url);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
            Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
            assertEquals(1, values.length);
            assertEquals(BASIC + " realm=\"Test Realm\"", values[0].getValue());
            HttpClientUtils.readResponse(result);
            if(badUser != null) {
                get = new HttpGet(url);
                get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString(badUser.getBytes(), false));
                result = client.execute(get);
                assertEquals(StatusCodes.FORBIDDEN, result.getStatusLine().getStatusCode());
                HttpClientUtils.readResponse(result);
            }
            get = new HttpGet(url);
            get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString(goodUser.getBytes(), false));
            get.addHeader("ExpectedMechanism", "BASIC");
            get.addHeader("ExpectedUser", goodUser.substring(0, goodUser.indexOf(':')));
            result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            //make sure that caching is disabled
            Assert.assertEquals("0", result.getHeaders("Expires")[0].getValue());
            Assert.assertEquals("no-cache", result.getHeaders("Pragma")[0].getValue());
            Assert.assertEquals("no-cache, no-store, must-revalidate", result.getHeaders("Cache-Control")[0].getValue());

            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(HELLO_WORLD, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
