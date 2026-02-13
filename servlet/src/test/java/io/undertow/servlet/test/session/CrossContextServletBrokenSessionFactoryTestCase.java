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

package io.undertow.servlet.test.session;


import io.undertow.server.handlers.PathHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSessionConfig;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * Test that separate servlet deployments use seperate session managers, even if one is broken.
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class CrossContextServletBrokenSessionFactoryTestCase {


    @BeforeClass
    public static void setup() throws ServletException {

        final ServletContainer container = ServletContainer.Factory.newInstance();
        final PathHandler path = new PathHandler();
        DefaultServer.setRootHandler(path);
        InMemorySessionManager manager = new InMemorySessionManager("test");

        // this deployment will deploy successfully
        createDeployment("1", container, path, manager);
        // this deployment will not deploy successfully
        createDeployment("2", container, path, manager);
    }

    private static void createDeployment(final String name, final ServletContainer container,  final PathHandler path, InMemorySessionManager sessionManager) throws ServletException {

        ServletInfo s = new ServletInfo("servlet", SessionServlet.class)
                .addMapping("/servlet");

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/" + name)
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName(name + ".war")
                .setSessionManagerFactory(deployment -> {
                    // mimic broken session factory for all deployments except first
                    return "1".equals(name) ? sessionManager : null;
                })
                .setDefaultSessionTimeout(1 /*second*/)
                .setServletSessionConfig(new ServletSessionConfig().setPath("/"))
                .addServlets(s);
        try {
            DeploymentManager manager = container.addDeployment(builder);
            manager.deploy();
            path.addPrefixPath(builder.getContextPath(), manager.start());
        } catch (Exception ignore) {
            // mimic Wildfly which issues an error to the log
            // and otherwise completely ignores this exception
        }
    }

    @Test
    public void testSharedSessionCookieMultipleDeployments() throws Exception {
        // FIXME UNDERTOW-2716 this test fails on Windows very frequently
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet direct1 = new HttpGet(DefaultServer.getDefaultServerURL() + "/1/servlet");
            client.execute(direct1, result -> {
            Assert.assertEquals(StatusCodes.OK, result.getCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);
                return null;
            });

            client.execute(direct1, result -> {
            Assert.assertEquals(StatusCodes.OK, result.getCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("2", response);
                return null;
            });

            Thread.sleep(1000); // await session timeout
            client.execute(direct1, result -> {
            Assert.assertEquals(StatusCodes.OK, result.getCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);
                return null;
            });
        }
    }
}
