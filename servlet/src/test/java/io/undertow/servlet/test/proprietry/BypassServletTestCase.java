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

package io.undertow.servlet.test.proprietry;

import io.undertow.io.IoCallback;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestListener;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class BypassServletTestCase {

    @BeforeClass
    public static void setup() throws ServletException {

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlet(
                        new ServletInfo("servlet", MessageServlet.class)
                                .addMapping("/")
                                .addInitParam(MessageServlet.MESSAGE, "This is a servlet")
                )
                .addListener(new ListenerInfo(TestListener.class))
                .addInitialHandlerChainWrapper(handler -> exchange -> {
                    if (exchange.getRelativePath().equals("/async")) {
                        exchange.getResponseSender().send("This is not a servlet", IoCallback.END_EXCHANGE);
                    } else {
                        handler.handleRequest(exchange);
                    }
                });

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }


    @Test
    public void testServletRequest() throws IOException {
        TestListener.init(2);
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/aa");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("This is a servlet", response);
                return null;
            });
            Assert.assertArrayEquals(new String[]{"created REQUEST", "destroyed REQUEST"}, TestListener.results().toArray());
        }
    }

    @Test
    public void testServletBypass() throws IOException {
        TestListener.init(0);
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/async");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("This is not a servlet", response);
                return null;
            });
            Assert.assertArrayEquals(new String[0], TestListener.results().toArray());
        }
    }
}
