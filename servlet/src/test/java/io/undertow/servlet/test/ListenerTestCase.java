/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.test;

import java.io.IOException;

import javax.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.spec.ServletContextImpl;
import io.undertow.servlet.test.runner.ServletServer;
import io.undertow.servlet.test.runner.ServletServer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(ServletServer.class)
public class ListenerTestCase {

    static DeploymentManager manager;

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance(root);

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletServerTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER)
                .addServlet(
                        new ServletInfo("servlet", SimpleServlet.class)
                                .addMapping("/aa")
                )
                .addListener(new ListenerInfo(TestListener.class));


        manager = container.addDeployment(builder);
        manager.deploy();
        manager.start();

        ServletServer.setRootHandler(root);
    }

    @Test
    public void testServletContextInitialized() throws IOException {
        Assert.assertNotNull(TestListener.servletContextInitializedEvent);
    }

    @Test
    public void testServletContextAttributeListener() throws IOException {
        ServletContextImpl sc = manager.getDeployment().getServletContext();
        sc.setAttribute("test", "1");
        Assert.assertNotNull(TestListener.servletContextAttributeEvent);
        Assert.assertEquals(TestListener.servletContextAttributeEvent.getName(), "test");
        Assert.assertEquals(TestListener.servletContextAttributeEvent.getValue(), "1");
        sc.setAttribute("test", "2");
        Assert.assertEquals(TestListener.servletContextAttributeEvent.getName(), "test");
        Assert.assertEquals(TestListener.servletContextAttributeEvent.getValue(), "1");
        sc.setAttribute("test", "3");
        Assert.assertEquals(TestListener.servletContextAttributeEvent.getName(), "test");
        Assert.assertEquals(TestListener.servletContextAttributeEvent.getValue(), "2");
        sc.removeAttribute("test");
        Assert.assertEquals(TestListener.servletContextAttributeEvent.getName(), "test");
        Assert.assertEquals(TestListener.servletContextAttributeEvent.getValue(), "3");
    }


}
