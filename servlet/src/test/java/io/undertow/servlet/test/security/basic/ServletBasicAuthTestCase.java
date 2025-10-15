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

package io.undertow.servlet.test.security.basic;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.AuthMethodConfig;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.security.SendAuthTypeServlet;
import io.undertow.servlet.test.security.SendUsernameServlet;
import io.undertow.servlet.test.security.constraint.ServletIdentityManager;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FlexBase64;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletBasicAuthTestCase {
    private static final String REALM_NAME = "Servlet_Realm";

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler path = new PathHandler();

        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo usernameServlet = new ServletInfo("Username Servlet", SendUsernameServlet.class)
                .addMapping("/secured/username");

        ServletInfo authTypeServlet = new ServletInfo("Auth Type Servlet", SendAuthTypeServlet.class)
                .addMapping("/secured/authType");

        ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user1", "password1", "role1");
        identityManager.addUser("charsetUser", "password-ü", "role1");

        LoginConfig loginConfig = new LoginConfig(REALM_NAME);
        Map<String, String> props = new HashMap<>();
        props.put("charset", "ISO_8859_1");
        props.put("user-agent-charsets", "Chrome,UTF-8,OPR,UTF-8");
        loginConfig.addFirstAuthMethod(new AuthMethodConfig("BASIC", props));
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setIdentityManager(identityManager)
                .setLoginConfig(loginConfig)
                .addServlets(usernameServlet, authTypeServlet);

        builder.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/secured/*"))
                .addRoleAllowed("role1")
                .setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.DENY));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        path.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(path);
    }

    @Test
    public void testChallengeSent() throws Exception {
        TestHttpClient client = new TestHttpClient();
        String url = DefaultServer.getDefaultServerURL() + "/servletContext/secured/username";
        HttpGet get = new HttpGet(url);
        HttpResponse result = client.execute(get);
        HttpClientUtils.readResponse(result);
        assertEquals(StatusCodes.UNAUTHORIZED, result.getStatusLine().getStatusCode());
        Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
        assertEquals(1, values.length);
        String value = values[0].getValue();
        assertTrue(value.startsWith("Basic"));
    }

    @Test
    public void testUserName() throws Exception {
        testCall("username", "user1", StandardCharsets.UTF_8, "Chrome", "user1", "password1", 200);
    }

    @Test
    public void testAuthType() throws Exception {
        testCall("authType", "BASIC", StandardCharsets.UTF_8, "Chrome", "user1", "password1", 200);
    }

    @Test
    public void testBasicAuthNonAscii() throws Exception {
        testCall("authType", "BASIC", StandardCharsets.UTF_8, "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36", "charsetUser", "password-ü", 200);
        testCall("authType", "BASIC", StandardCharsets.ISO_8859_1, "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36", "charsetUser", "password-ü", 401);
        testCall("authType", "BASIC", StandardCharsets.ISO_8859_1, "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1", "charsetUser", "password-ü", 200);
        testCall("authType", "BASIC", StandardCharsets.UTF_8, "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1", "charsetUser", "password-ü", 401);
    }

    public void testCall(final String path, final String expectedResponse, Charset charset, String userAgent, String user, String password, int expect) throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            String url = DefaultServer.getDefaultServerURL() + "/servletContext/secured/" + path;
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
