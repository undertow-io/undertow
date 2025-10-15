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
package io.undertow.servlet.test.listener.ordering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.EmptyServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.Tracker;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import io.undertow.testutils.TestHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @see https://issues.jboss.org/browse/UNDERTOW-23
 *
 * @author Jozef Hartinger
 *
 */
@RunWith(DefaultServer.class)
public class ServletSessionListenerOrderingTestCase {

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler path = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/listener")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("listener.war")
                .addListener(new ListenerInfo(FirstListener.class))
                .addListener(new ListenerInfo(SecondListener.class))
                .addServlet(new ServletInfo("message", EmptyServlet.class)
                        .addMapping("/*"));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        path.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(path);
    }

    @Test
    public void testSimpleSessionUsage() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            Tracker.reset();
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/listener/test");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());

            List<String> expected = new ArrayList<>();
            expected.add(FirstListener.class.getSimpleName());
            expected.add(SecondListener.class.getSimpleName());
            expected.add(SecondListener.class.getSimpleName());
            expected.add(FirstListener.class.getSimpleName());

            Assert.assertEquals(expected, Tracker.getActions());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
