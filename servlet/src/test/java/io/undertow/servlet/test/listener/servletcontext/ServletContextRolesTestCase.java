/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.listener.servletcontext;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static org.junit.Assert.assertEquals;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.AuthMethodConfig;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.test.security.constraint.ServletIdentityManager;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FlexBase64;
import io.undertow.util.Headers;

/**
 * @author baranowb
 */
@RunWith(DefaultServer.class)
public class ServletContextRolesTestCase {
    private static final String REALM_NAME = "Servlet_Realm-1";
    static DeploymentManager manager;

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();
        final ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user1", "password1", "unspecified-role");

        LoginConfig loginConfig = new LoginConfig(REALM_NAME);
        Map<String, String> props = new HashMap<>();
        props.put("charset", "ISO_8859_1");
        props.put("user-agent-charsets", "Chrome,UTF-8,OPR,UTF-8");
        loginConfig.addFirstAuthMethod(new AuthMethodConfig("BASIC", props));

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(ServletContextRolesTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlet(
                        new ServletInfo("servlet", CheckRolesServlet.class)
                                .addMapping("/aa")
                )
                .addListener(new ListenerInfo(DeclareRolesServletContextListener.class))
                .setIdentityManager(identityManager)
                .setLoginConfig(loginConfig);

        builder.addPrincipalVsRoleMappings("user1", DeclareRolesServletContextListener.ROLES);
        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/*"))
                .addRolesAllowed(DeclareRolesServletContextListener.ROLES)
                .setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.DENY));

        manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testRoles() throws Exception {
        final StringBuilder sb = new StringBuilder(40);
        sb.append("dobby:true\n")
        .append("was:true\n")
        .append("here:true\n");
        testCall(sb.toString(), StandardCharsets.UTF_8, "Chrome", "user1", "password1", 200);
    }

    public void testCall( final String expectedResponse, Charset charset, String userAgent, String user, String password, int expect) throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            String url = DefaultServer.getDefaultServerURL() + "/servletContext/aa";
            HttpGet get = new HttpGet(url);
            get = new HttpGet(url);
            get.addHeader(Headers.USER_AGENT_STRING, userAgent);
            get.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString((user + ":" + password).getBytes(charset), false));
            HttpResponse result = client.execute(get);
            assertEquals(expect, result.getStatusLine().getStatusCode());

            final String response = HttpClientUtils.readResponse(result);
            if(expect == 200) {
                assertEquals(expectedResponse, response);
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
