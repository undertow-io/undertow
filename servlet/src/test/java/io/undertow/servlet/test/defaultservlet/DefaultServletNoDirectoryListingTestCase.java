/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.defaultservlet;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.handlers.DefaultServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author rmartinc
 */
@RunWith(DefaultServer.class)
public class DefaultServletNoDirectoryListingTestCase {

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(DefaultServletNoDirectoryListingTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setResourceManager(new TestResourceLoader(DefaultServletNoDirectoryListingTestCase.class));

        builder.addServlet(new ServletInfo("default", DefaultServlet.class)
                .addMapping("/*"));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testDirectoryListing() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            try (CloseableHttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path"))) {
                Assert.assertEquals(StatusCodes.FORBIDDEN, result.getStatusLine().getStatusCode());
            }
            try (CloseableHttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path?js"))) {
                Assert.assertEquals(StatusCodes.FORBIDDEN, result.getStatusLine().getStatusCode());
            }
            try (CloseableHttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/path?css"))) {
                Assert.assertEquals(StatusCodes.FORBIDDEN, result.getStatusLine().getStatusCode());
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
