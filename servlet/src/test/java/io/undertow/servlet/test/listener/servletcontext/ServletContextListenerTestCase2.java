/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.listener.servletcontext;

import java.io.IOException;
import java.util.Collections;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import jakarta.servlet.ServletException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletContextListenerTestCase2 {

    static DeploymentManager manager;

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServletContainerInitializer(new ServletContainerInitializerInfo(TestSci.class, Collections.<Class<?>>emptySet()))
                .addServlet(
                        new ServletInfo("servlet", MessageServlet.class)
                                .addMapping("/aa")
                )
                .addListener(new ListenerInfo(ServletContextTestListener.class));


        manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testServletContextDestroyed() throws IOException {
        manager.undeploy();
        Assert.assertNotNull(ServletContextTestListener.servletContextDestroyedEvent);
    }

}
