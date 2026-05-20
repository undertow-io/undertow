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
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.List;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class SessionIdHandlingTestCase {


    @BeforeClass
    public static void setup() throws ServletException {


        final PathHandler pathHandler = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlets(new ServletInfo("servlet", RequestedSessionIdServlet.class)
                        .addMapping("/session"));
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
    public void testGetRequestedSessionId() throws IOException {
        CookieStore cookieStore = new BasicCookieStore();
        try (CloseableHttpClient client = TestHttpClient.custom().setDefaultCookieStore(cookieStore).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=create");
            String sessionId = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("null", response);
                return getSession(cookieStore.getCookies());
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=default");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals(sessionId, response);
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=change");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals(sessionId, response);
                return null;
            });

            String newSessionId = getSession(cookieStore.getCookies());
            Assert.assertNotEquals(sessionId, newSessionId);
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=default");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals(newSessionId, response);
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=destroycreate");
            final String createdSessionId = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals(newSessionId, response);
                return getSession(cookieStore.getCookies());
            });
            Assert.assertNotEquals(createdSessionId, newSessionId);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=destroy");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals(createdSessionId, response);
                return null;
            });
        }
    }


    @Test
    public void testIsRequestedSessionIdValid() throws IOException, InterruptedException {
        CookieStore cookieStore = new BasicCookieStore();
        try (CloseableHttpClient client = TestHttpClient.custom().setDefaultCookieStore(cookieStore).build()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=create");
            String sessionId = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("null", response);
                return getSession(cookieStore.getCookies());
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=timeout");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals(sessionId, response);
                return null;
            });
            Thread.sleep(2500);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=isvalid");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("false", response);
                return null;
            });
        }
    }

    private String getSession(List<Cookie> cookies) {
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals("JSESSIONID")) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
