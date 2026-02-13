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

package io.undertow.servlet.test.security.form;

import io.undertow.servlet.api.AuthMethodConfig;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import java.util.Map;

import jakarta.servlet.ServletException;

import io.undertow.security.api.AuthenticationMode;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.security.SendUsernameServlet;
import io.undertow.servlet.test.security.constraint.ServletIdentityManager;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletFormAuthTestCase {

    public static final String HELLO_WORLD = "Hello World";
    private static final String DEFAULT_PAGE = "/main.html";

    public static CloseableHttpClient clientWithSimpleRedirectStrategy() {
        return TestHttpClient.custom().setRedirectStrategy(new DefaultRedirectStrategy() {
            @Override
            public boolean isRedirected(final HttpRequest request, final HttpResponse response, final HttpContext context) throws ProtocolException {
                return (response.getCode() == StatusCodes.FOUND) || super.isRedirected(request, response, context);
            }
        }).build();
    }

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler path = new PathHandler();

        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", SendUsernameServlet.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("role1"))
                .addMapping("/secured/*");

        ServletInfo echo = new ServletInfo("echo", EchoServlet.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("role1"))
                .addMapping("/secured/echo");

        ServletInfo echoParam = new ServletInfo("echoParam", RequestParamEchoServlet.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("role1"))
                .addMapping("/secured/echoParam");

        ServletInfo s1 = new ServletInfo("loginPage", FormLoginServlet.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("group1"))
                .addMapping("/FormLoginServlet");


        ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user1", "password1", "role1");

        Map<String, String> props = new HashMap<>();
        props.put("default_page", DEFAULT_PAGE);
        AuthMethodConfig authMethodConfig = new AuthMethodConfig("FORM", props);

        LoginConfig loginConfig = new LoginConfig("Test Realm", "/FormLoginServlet", "/error.html").addFirstAuthMethod(authMethodConfig);

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setAuthenticationMode(AuthenticationMode.CONSTRAINT_DRIVEN)
                .setIdentityManager(identityManager)
                .setLoginConfig(loginConfig)
                .addServlets(s, s1, echo, echoParam);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        path.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(path);
    }

    @Test
    public void testServletFormAuth() throws IOException {
        try (CloseableHttpClient client = clientWithSimpleRedirectStrategy()) {
            final String uri = DefaultServer.getDefaultServerURL() + "/servletContext/secured/test";
            HttpGet get = new HttpGet(uri);
            client.execute(get, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.startsWith("j_security_check"));
                return null;
            });

            BasicNameValuePair[] pairs = new BasicNameValuePair[]{new BasicNameValuePair("j_username", "user1"), new BasicNameValuePair("j_password", "password1")};
            final List<NameValuePair> data = new ArrayList<>();
            data.addAll(Arrays.asList(pairs));
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/j_security_check;jsessionid=dsjahfklsahdfjklsa");

            post.setEntity(new UrlEncodedFormEntity(data));

            client.execute(post, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("user1", response);
                return null;
            });
        }
    }

    @Test
    public void testServletFormAuthWithSavedPostBody() throws IOException {
        try (CloseableHttpClient client = clientWithSimpleRedirectStrategy()) {
            final String uri = DefaultServer.getDefaultServerURL() + "/servletContext/secured/echo";
            HttpPost post = new HttpPost(uri);
            post.setEntity(new StringEntity("String Entity"));
            client.execute(post, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.startsWith("j_security_check"));
                return null;
            });

            BasicNameValuePair[] pairs = new BasicNameValuePair[]{new BasicNameValuePair("j_username", "user1"), new BasicNameValuePair("j_password", "password1")};
            final List<NameValuePair> data = new ArrayList<>();
            data.addAll(Arrays.asList(pairs));
            post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/j_security_check");

            post.setEntity(new UrlEncodedFormEntity(data));

            client.execute(post, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("String Entity", response);
                return null;
            });
        }
    }

    @Test
    public void testServletFormAuthWithoutSavedPostBody() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.custom().setRedirectStrategy(new DefaultRedirectStrategy() {
            @Override
            public boolean isRedirected(final HttpRequest request, final HttpResponse response, final HttpContext context) throws ProtocolException {
                if (response.getCode() == StatusCodes.FOUND) {
                    return true;
                }
                try {
                    if (((HttpClientContext) context).getRequest().getUri().toString().equals(DefaultServer.getDefaultServerURL() + DEFAULT_PAGE)) {
                        response.setCode(StatusCodes.OK);
                        // Skip redirecting, because the resource isn't available in this test
                        return false;
                    }
                } catch (URISyntaxException e) {
                    Assert.fail("exception thrown");
                }
                // force the test to fail
                response.setCode(StatusCodes.EXPECTATION_FAILED);
                return super.isRedirected(request, response, context);
            }
        }).build()) {
            BasicNameValuePair[] pairs = new BasicNameValuePair[]{new BasicNameValuePair("j_username", "user1"), new BasicNameValuePair("j_password", "password1")};
            final List<NameValuePair> data = new ArrayList<>();
            data.addAll(Arrays.asList(pairs));
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/j_security_check");

            post.setEntity(new UrlEncodedFormEntity(data));

            client.execute(post, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("", response);
                return null;
            });
        }
    }

    @Test
    public void testServletFormAuthWithOriginalRequestParams() throws IOException {
        try (CloseableHttpClient client = clientWithSimpleRedirectStrategy()) {
            final String uri = DefaultServer.getDefaultServerURL() + "/servletContext/secured/echoParam?param=developer";
            HttpPost post = new HttpPost(uri);
            post.setEntity(new StringEntity("String Entity"));
            client.execute(post, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertTrue(response.startsWith("j_security_check"));
                return null;
            });

            BasicNameValuePair[] pairs = new BasicNameValuePair[]{new BasicNameValuePair("j_username", "user1"), new BasicNameValuePair("j_password", "password1")};
            final List<NameValuePair> data = new ArrayList<>();
            data.addAll(Arrays.asList(pairs));
            post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/j_security_check");

            post.setEntity(new UrlEncodedFormEntity(data));

            client.execute(post, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                assertEquals("developer", response);
                return null;
            });
        }
    }
}
