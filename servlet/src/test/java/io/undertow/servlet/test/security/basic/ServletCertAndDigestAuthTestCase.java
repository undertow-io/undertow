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

import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static org.junit.Assert.assertEquals;
import static org.xnio.Options.SSL_CLIENT_AUTH_MODE;
import static org.xnio.SslClientAuthMode.NOT_REQUESTED;

import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;
import jakarta.servlet.MultipartConfigElement;

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
import io.undertow.servlet.test.security.MultipartAcceptingServlet;
import io.undertow.servlet.test.security.constraint.ServletIdentityManager;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FlexBase64;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

/**
 * @author Tomas Hofman
 */
@RunWith(DefaultServer.class)
public class ServletCertAndDigestAuthTestCase {

    private static final String REALM_NAME = "Servlet_Realm";
    private static final String BASE_PATH = "/servletContext/secured/";

    private static SSLContext clientSSLContext;

    @BeforeClass
    public static void startSSL() throws Exception {
        DefaultServer.startSSLServer(OptionMap.create(SSL_CLIENT_AUTH_MODE, NOT_REQUESTED));
        clientSSLContext = DefaultServer.getClientSSLContext();


        final PathHandler path = new PathHandler();

        final ServletContainer container = ServletContainer.Factory.newInstance();
        ServletInfo multipartServlet = new ServletInfo("Multipart Accepting Servlet", MultipartAcceptingServlet.class)
                .addMapping("/secured/multipart")
                .setMultipartConfig(new MultipartConfigElement(""));

        ServletIdentityManager identityManager = new ServletIdentityManager();
        identityManager.addUser("user1", "password1", "role1");
        identityManager.addUser("charsetUser", "password-ü", "role1");

        LoginConfig loginConfig = new LoginConfig(REALM_NAME);
        loginConfig.addFirstAuthMethod(new AuthMethodConfig("BASIC"));
        loginConfig.addFirstAuthMethod(new AuthMethodConfig("CLIENT_CERT"));
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setIdentityManager(identityManager)
                .setLoginConfig(loginConfig)
                .addServlets(multipartServlet);

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

    @AfterClass
    public static void stopSSL() throws Exception {
        clientSSLContext = null;
        DefaultServer.stopSSLServer();
    }

    @Test
    public void testMultipartRequest() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("0123456789");
        }

        try (TestHttpClient client = new TestHttpClient()) {
            // create POST request
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addPart("part1", new ByteArrayBody(sb.toString().getBytes(), "file.txt"));
            builder.addPart("part2", new StringBody("0123456789", ContentType.TEXT_HTML));
            HttpEntity entity = builder.build();

            client.setSSLContext(clientSSLContext);
            String url = DefaultServer.getDefaultServerSSLAddress() + BASE_PATH + "multipart";
            HttpPost post = new HttpPost(url);
            post.setEntity(entity);
            post.addHeader(AUTHORIZATION.toString(), BASIC + " " + FlexBase64.encodeString(("user1" + ":" + "password1").getBytes(StandardCharsets.UTF_8), false));

            HttpResponse result = client.execute(post);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
        }
    }
}
