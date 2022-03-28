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
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.cookie.Cookie;
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
        TestHttpClient client = new TestHttpClient();
        try {

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=create");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("null", response);
            String sessionId = getSession(client.getCookieStore().getCookies());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=default");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(sessionId, response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=change");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(sessionId, response);

            String newSessionId = getSession(client.getCookieStore().getCookies());
            Assert.assertNotEquals(sessionId, newSessionId);
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=default");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(newSessionId, response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=destroycreate");
            result = client.execute(get);
            final String createdSessionId = getSession(client.getCookieStore().getCookies());
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(newSessionId, response);
            Assert.assertNotEquals(createdSessionId, newSessionId);


            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=destroy");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(createdSessionId, response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testIsRequestedSessionIdValid() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        try {

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=create");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("null", response);
            String sessionId = getSession(client.getCookieStore().getCookies());

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=timeout");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(sessionId, response);
            Thread.sleep(2500);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/session?action=isvalid");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("false", response);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private String getSession(List<Cookie> cookies) {
        for(Cookie cookie : cookies) {
            if(cookie.getName().equals("JSESSIONID")) {
                return cookie.getValue();
            }
        }
        return null;
    }


}
