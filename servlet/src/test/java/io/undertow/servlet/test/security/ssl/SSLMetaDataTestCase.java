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
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.TestClassIntrospector;
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
public class SSLMetaDataTestCase {

    @BeforeClass
    public static void setup() throws Exception {
        DefaultServer.startSSLServer();

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("servlet", SSLAttributesServlet.class)
                .addMapping("/id")
                .addMapping("/cert")
                .addMapping("/key-size")
                .addMapping("/cipher-suite");

        DeploymentInfo info = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlet(s);


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
    public void testSessionId() throws IOException {
        internalTest("/id");
    }

    @Test
    public void testCipherSuite() throws IOException {
        internalTest("/cipher-suite");
    }

    @Test
    public void testKeySize() throws IOException {
        internalTest("/key-size");
    }

    @Test
    public void testCert() throws IOException {
        internalTest("/cert");
    }

    private void internalTest(final String path) throws IOException {
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());

        final String url = DefaultServer.getDefaultServerSSLAddress() + "/servletContext" + path;
        try {
            HttpGet get = new HttpGet(url);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertTrue(response.length() > 0);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
