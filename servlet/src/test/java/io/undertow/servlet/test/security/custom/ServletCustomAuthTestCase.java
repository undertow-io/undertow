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
package io.undertow.servlet.test.security.custom;

import static org.junit.Assert.assertEquals;

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
import io.undertow.servlet.test.security.form.FormLoginServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.ServletException;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test case that validates the use of the DeploymentManagerImpl authMechanism override
 *
 * @author Stuart Douglas
 * @author Anil Saldhana
 */
@RunWith(DefaultServer.class)
public class ServletCustomAuthTestCase {

    public static final String HELLO_WORLD = "Hello World";

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler path = new PathHandler();

        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", SendUsernameServlet.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("role1"))
                .addMapping("/secured/*");

        ServletInfo s1 = new ServletInfo("loginPage", FormLoginServlet.class)
                .setServletSecurityInfo(new ServletSecurityInfo()
                        .addRoleAllowed("group1"))
                .addMapping("/FormLoginServlet");


        ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user1", "password1", "role1");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setIdentityManager(identityManager)
                .setLoginConfig(new LoginConfig("FORM", "Test Realm", "/FormLoginServlet", "/error.html"))
                .addServlets(s, s1)
                .addAuthenticationMechanism("FORM", CustomAuthenticationMechanism.FACTORY);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        path.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(path);
    }

    @Test
    public void testServletCustomFormAuth() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.custom().setRedirectStrategy(new DefaultRedirectStrategy() {
            @Override
            public boolean isRedirected(final HttpRequest request, final HttpResponse response, final HttpContext context) throws ProtocolException {
                if (response.getCode() == StatusCodes.FOUND) {
                    return true;
                }
                return super.isRedirected(request, response, context);
            }
        }).build()) {
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
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/" + CustomAuthenticationMechanism.POST_LOCATION);

            post.setEntity(new UrlEncodedFormEntity(data));

            client.execute(post, result -> {
                assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("user1", response);
                return null;
            });
        }
    }
}
