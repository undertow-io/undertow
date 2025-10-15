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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.SSLContext;
import jakarta.servlet.ServletException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.X509CertificateCredential;
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
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletClientCertAuthTestCase {
    private static final String REALM_NAME = "Servlet_Realm";

    protected static final IdentityManager identityManager;
    private static SSLContext clientSSLContext;

    static {

        final Set<String> certUsers = new HashSet<>();
        certUsers.add("CN=Test Client,OU=OU,O=Org,L=City,ST=State,C=GB");
        identityManager = new IdentityManager() {

            @Override
            public Account verify(Account account) {
                // An existing account so for testing assume still valid.
                return account;
            }

            @Override
            public Account verify(String id, Credential credential) {
                return null;
            }

            @Override
            public Account verify(Credential credential) {
                if (credential instanceof X509CertificateCredential) {
                    final Principal p = ((X509CertificateCredential) credential).getCertificate().getSubjectX500Principal();
                    if (certUsers.contains(p.getName())) {
                        return new Account() {

                            @Override
                            public Principal getPrincipal() {
                                return p;
                            }

                            @Override
                            public Set<String> getRoles() {
                                return Collections.singleton("role1");
                            }

                        };
                    }

                }
                return null;
            }
        };

    }

    @BeforeClass
    public static void startSSL() throws Exception {
    }

    @AfterClass
    public static void stopSSL() throws Exception {
        clientSSLContext = null;
        DefaultServer.stopSSLServer();
    }

    @BeforeClass
    public static void setup() throws ServletException, IOException {
        DefaultServer.startSSLServer();
        clientSSLContext = DefaultServer.getClientSSLContext();


        final PathHandler path = new PathHandler();

        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo usernameServlet = new ServletInfo("Username Servlet", SendUsernameServlet.class)
                .addMapping("/secured/username");

        ServletInfo authTypeServlet = new ServletInfo("Auth Type Servlet", SendAuthTypeServlet.class)
                .addMapping("/secured/authType");

        LoginConfig loginConfig = new LoginConfig(REALM_NAME);
        loginConfig.addFirstAuthMethod(new AuthMethodConfig("CLIENT_CERT"));
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
    public void testUserName() throws Exception {
        testCall("username", "CN=Test Client,OU=OU,O=Org,L=City,ST=State,C=GB", 200);
    }

    @Test
    public void testAuthType() throws Exception {
        testCall("authType", "CLIENT_CERT", 200);
    }


    public void testCall(final String path, final String expectedResponse, int expect) throws Exception {
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(clientSSLContext);
        try {
            String url = DefaultServer.getDefaultServerSSLAddress() + "/servletContext/secured/" + path;
            HttpGet get = new HttpGet(url);
            HttpResponse result = client.execute(get);
            assertEquals(expect, result.getStatusLine().getStatusCode());

            final String response = HttpClientUtils.readResponse(result);
            if (expect == 200) {
                assertEquals(expectedResponse, response);
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
