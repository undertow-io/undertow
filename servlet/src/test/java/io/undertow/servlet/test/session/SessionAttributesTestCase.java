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
package io.undertow.servlet.test.session;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import io.undertow.UndertowOptions;
import io.undertow.server.handlers.PathHandler;
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
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.SessionTrackingMode;

/**
 * @author baranowb
 */
@RunWith(DefaultServer.class)
public class SessionAttributesTestCase {


    @BeforeClass
    public static void setup() throws ServletException {


        final PathHandler pathHandler = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SessionAttributesTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addListener(new ListenerInfo(SessionAttributesTestCase.SessionCookieConfigListener.class))
                .addServlets(new ServletInfo("servlet", SessionServlet.class)
                        .addMappings("/aa/attributes"));
        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        try {
            pathHandler.addPrefixPath(builder.getContextPath(), manager.start());
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        DefaultServer.setUndertowOptions(OptionMap.create(UndertowOptions.ENABLE_RFC6265_COOKIE_VALIDATION, Boolean.TRUE));
        DefaultServer.setRootHandler(pathHandler);
    }

    @AfterClass
    public static void deSetup() throws ServletException {
        DefaultServer.setUndertowOptions(OptionMap.EMPTY);
    }

    @Test
    public void testSameSiteAndCustomAttribute() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/aa/attributes");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);
            String cookieValue = result.getHeaders("Set-Cookie")[0].getValue();
            Assert.assertTrue(cookieValue, cookieValue.contains("MySessionCookie"));
            Assert.assertTrue(cookieValue, cookieValue.contains("/servletContext/aa/"));
            Assert.assertTrue(cookieValue, cookieValue.contains("SameSite=Strict"));
            Assert.assertTrue(cookieValue, cookieValue.contains("Space=Above&Beyond"));
            //double back to regular session test thats done by counterparts
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("2", response);

            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private static class SessionCookieConfigListener implements ServletContextListener {
        @Override
        public void contextInitialized(final ServletContextEvent sce) {
            final ServletContext servletContext = sce.getServletContext();
            servletContext.getSessionCookieConfig().setName("MySessionCookie");
            servletContext.getSessionCookieConfig().setPath("/servletContext/aa/");
            servletContext.getSessionCookieConfig().setAttribute("SameSite", "Strict");
            servletContext.getSessionCookieConfig().setAttribute("Space", "Above&Beyond");
            servletContext.setSessionTrackingModes(new HashSet<>(Arrays.asList(SessionTrackingMode.COOKIE, SessionTrackingMode.URL)));
        }

        @Override
        public void contextDestroyed(final ServletContextEvent sce) {

        }
    }
}