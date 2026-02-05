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

package io.undertow.servlet.test.session;

import io.undertow.server.handlers.PathHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DefaultServer.class)
public class MaxSessionLimitTestCase {
    @BeforeClass
    public static void setup() throws ServletException {

        final InMemorySessionManager sessionManager = new InMemorySessionManager("test", 1);

        final PathHandler pathHandler = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(MaxSessionLimitTestCase.class.getClassLoader())
                .setContextPath("/maxSession")
                .setSessionManagerFactory(deployment -> sessionManager)
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("maxSession.war")
                .addListener(new ListenerInfo(SessionCookieConfigListener.class))
                .addServlets(new ServletInfo("servlet", SessionServlet.class).addMapping("/"));
        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        try {
            pathHandler.addPrefixPath(builder.getContextPath(), manager.start());
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        DefaultServer.setRootHandler(pathHandler);
    }

    @Test
    public void testMaxSessionHandling() throws Exception {
        try (TestHttpClient client = new TestHttpClient();
             TestHttpClient client2 = new TestHttpClient()) {

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/maxSession");

            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("1", HttpClientUtils.readResponse(result));

            // The second request should fail as the first request has maxed out the session limit
            HttpResponse result2 = client2.execute(get);
            Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result2.getStatusLine().getStatusCode());
        }
    }
}
