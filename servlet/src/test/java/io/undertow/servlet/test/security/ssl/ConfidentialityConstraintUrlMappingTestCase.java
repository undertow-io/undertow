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
package io.undertow.servlet.test.security.ssl;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.SecurityInfo.EmptyRoleSemantic;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.api.WebResourceCollection;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.security.SendSchemeServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestConfidentialPortManager;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Test case to test transport-guarantee enforcement.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(DefaultServer.class)
public class ConfidentialityConstraintUrlMappingTestCase {


    @BeforeClass
    public static void setup() throws Exception {
        DefaultServer.startSSLServer();

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", SendSchemeServlet.class)
                .addMapping("/clear")
                .addMapping("/integral")
                .addMapping("/confidential");

        DeploymentInfo info = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setConfidentialPortManager(TestConfidentialPortManager.INSTANCE)
                .addServlet(s);

        info.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                .addUrlPattern("/integral"))
                .setTransportGuaranteeType(TransportGuaranteeType.INTEGRAL)
                .setEmptyRoleSemantic(EmptyRoleSemantic.PERMIT));

        info.addSecurityConstraint(new SecurityConstraint()
                .addWebResourceCollection(new WebResourceCollection()
                .addUrlPattern("/confidential"))
                .setTransportGuaranteeType(TransportGuaranteeType.CONFIDENTIAL)
                .setEmptyRoleSemantic(EmptyRoleSemantic.PERMIT));

        DeploymentManager manager = container.addDeployment(info);
        manager.deploy();
        root.addPrefixPath(info.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @AfterClass
    public static void cleanUp() throws Exception {
        DefaultServer.stopSSLServer();
    }

    @Test
    public void testClear() throws IOException {
        internalTest("/clear", "http");
    }

    @Test
    public void testIntegral() throws IOException {
        internalTest("/integral", "https");
    }

    @Test
    public void testConfidential() throws IOException {
        internalTest("/confidential", "https");
    }

    private void internalTest(final String path, final String expectedScheme) throws IOException {
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());

        final String url = DefaultServer.getDefaultServerURL() + "/servletContext" + path;
        try {
            HttpGet get = new HttpGet(url);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(expectedScheme, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
