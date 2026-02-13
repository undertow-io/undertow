/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FlexBase64;
import jakarta.servlet.ServletException;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.function.Supplier;

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.Headers.WWW_AUTHENTICATE;
import static io.undertow.util.StatusCodes.FORBIDDEN;
import static io.undertow.util.StatusCodes.OK;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import static org.junit.Assert.assertEquals;

/**
 * Test that verifies correct handling of &lt;deny-uncovered-http-methods/&gt;.
 *
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
public class DenyUncoveredHttpMethodsTestCase {

    public static final String HELLO_WORLD = "Hello World";

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", AuthenticationMessageServlet.class)
                .addInitParam(MessageServlet.MESSAGE, HELLO_WORLD)
                .addMapping("/vanilla")
                .addMapping("/fully-covered")
                .addMapping("/fully-covered-permit-no-role")
                .addMapping("/fully-covered-deny-no-role")
                .addMapping("/omitted-methods")
                .addMapping("/omitted-methods-deny-empty-role");

        ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user", "password", "role");
        identityManager.addUser("unauthorized-user", "password", "unauthorized-role");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(DenyUncoveredHttpMethodsTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setIdentityManager(identityManager)
                .setLoginConfig(new LoginConfig("BASIC", "Test Realm"))
                .addServlet(s);

        // deny uncovered http methods
        builder.setDenyUncoveredHttpMethods(true);

        builder.addSecurityConstraint(new SecurityConstraint().addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/vanilla").addHttpMethod("GET").addHttpMethod("POST"))
                .addRoleAllowed("role").setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.PERMIT));


        builder.addSecurityConstraint(new SecurityConstraint().addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/fully-covered"))
                .addRoleAllowed("role"));

        builder.addSecurityConstraint(new SecurityConstraint().addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/fully-covered-permit-no-role"))
                .addRoleAllowed("role").setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.PERMIT));

        builder.addSecurityConstraint(new SecurityConstraint().addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/fully-covered-deny-no-role"))
                .addRoleAllowed("role").setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.DENY));

        builder.addSecurityConstraint(new SecurityConstraint().addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/omitted-methods").addHttpMethodOmission("GET"))
                .setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.PERMIT));

        builder.addSecurityConstraint(new SecurityConstraint().addWebResourceCollection(new WebResourceCollection()
                        .addUrlPattern("/omitted-methods-deny-empty-role").addHttpMethodOmission("GET"))
                .setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.DENY));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(root);
    }

    // TRACE is omitted since HttpClient doesn't allow it to have an Authorization header
    @Test
    public void testVanillaSecurityConstraint() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final String url = DefaultServer.getDefaultServerURL() + "/servletContext/vanilla";
            testAccessible(() -> new HttpGet(url), client, false);
            testAccessible(() -> new HttpPost(url), client, false);
            testForbidden(() -> new HttpPut(url), client);
            testForbidden(() -> new HttpDelete(url), client);
            testForbidden(() -> new HttpOptions(url), client);
        }
    }

    @Test
    public void testFullyCoveredConstraint() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final String url = DefaultServer.getDefaultServerURL() + "/servletContext/fully-covered";
            testAccessible(() -> new HttpGet(url), client, false);
            testAccessible(() -> new HttpPost(url), client, false);
            testAccessible(() -> new HttpPut(url), client, false);
            testAccessible(() -> new HttpDelete(url), client, false);
            testAccessible(() -> new HttpOptions(url), client, false);
        }
    }

    @Test
    public void testFullyCoveredPermitNoRoleConstraint() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final String url = DefaultServer.getDefaultServerURL() + "/servletContext/fully-covered-permit-no-role";
            testAccessible(() -> new HttpGet(url), client, false);
            testAccessible(() -> new HttpPost(url), client, false);
            testAccessible(() -> new HttpPut(url), client, false);
            testAccessible(() -> new HttpDelete(url), client, false);
            testAccessible(() -> new HttpOptions(url), client, false);
        }
    }

    @Test
    public void testFullyCoveredDenyNoRoleConstraint() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final String url = DefaultServer.getDefaultServerURL() + "/servletContext/fully-covered-deny-no-role";
            testAccessible(() -> new HttpGet(url), client, false);
            testAccessible(() -> new HttpPost(url), client, false);
            testAccessible(() -> new HttpPut(url), client, false);
            testAccessible(() -> new HttpDelete(url), client, false);
            testAccessible(() -> new HttpOptions(url), client, false);
        }
    }

    @Test
    public void testOmittedMethods() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            String url = DefaultServer.getDefaultServerURL() + "/servletContext/omitted-methods";
            testForbidden(() -> new HttpGet(url), client);
            testAccessible(() -> new HttpPost(url), client, true);
            testAccessible(() -> new HttpPut(url), client, true);
            testAccessible(() -> new HttpDelete(url), client, true);
            testAccessible(() -> new HttpOptions(url), client, true);
        }
    }

    @Test
    public void testOmittedMethodsDenyEmptyRole() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            String url = DefaultServer.getDefaultServerURL() + "/servletContext/omitted-methods-deny-empty-role";
            testForbidden(() -> new HttpGet(url), client);
            testForbidden(() -> new HttpPost(url), client);
            testForbidden(() -> new HttpPut(url), client);
            testForbidden(() -> new HttpDelete(url), client);
            testForbidden(() -> new HttpOptions(url), client);
        }
    }

    public void testAccessible(Supplier<HttpUriRequestBase> newRequest, CloseableHttpClient client, boolean noRolePermitted) throws IOException {
        HttpUriRequestBase request = newRequest.get();
        request.addHeader("ExpectedMechanism", "None");
        request.addHeader("ExpectedUser", "None");
        client.execute(request, result -> {
            if (noRolePermitted) {
                assertEquals(OK, result.getCode());
            } else {
                assertEquals(UNAUTHORIZED, result.getCode());
                Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
                assertEquals(1, values.length);
                assertEquals(BASIC + " realm=\"Test Realm\"", values[0].getValue());
            }
            return HttpClientUtils.readResponse(result);
        });

        request = newRequest.get();
        request.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("user:password".getBytes(), false));
        request.addHeader("ExpectedMechanism", "BASIC");
        request.addHeader("ExpectedUser", "user");
        client.execute(request, result -> {
            assertEquals(OK, result.getCode());
            String response = HttpClientUtils.readResponse(result);
            assertEquals(HELLO_WORLD, response);
            return null;
        });

        request = newRequest.get();
        request.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("unauthorized-user:password".getBytes(), false));
        request.addHeader("ExpectedMechanism", "BASIC");
        request.addHeader("ExpectedUser", "unauthorized-user");
        client.execute(request, result -> {
            if (noRolePermitted) {
                assertEquals(OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                assertEquals(HELLO_WORLD, response);
            } else {
                assertEquals(FORBIDDEN, result.getCode());
                HttpClientUtils.readResponse(result);
            }
            return null;
        });
    }


    public void testForbidden(Supplier<HttpUriRequestBase> newRequest, CloseableHttpClient client) throws IOException {
        HttpUriRequestBase request = newRequest.get();
        client.execute(request, result -> {
            assertEquals(FORBIDDEN, result.getCode());
            Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
            assertEquals(0, values.length);
            return HttpClientUtils.readResponse(result);
        });

        request = newRequest.get();
        request.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("user:password".getBytes(), false));
        request.addHeader("ExpectedMechanism", "BASIC");
        request.addHeader("ExpectedUser", "user");
        client.execute(request, result -> {
            assertEquals(FORBIDDEN, result.getCode());
            Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
            assertEquals(0, values.length);
            return HttpClientUtils.readResponse(result);
        });

        request = newRequest.get();
        request.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString("unauthorized-user:password".getBytes(), false));
        request.addHeader("ExpectedMechanism", "BASIC");
        request.addHeader("ExpectedUser", "user");
        client.execute(request, result -> {
            assertEquals(FORBIDDEN, result.getCode());
            Header[] values = result.getHeaders(WWW_AUTHENTICATE.toString());
            assertEquals(0, values.length);
            return HttpClientUtils.readResponse(result);
        });
    }
}
