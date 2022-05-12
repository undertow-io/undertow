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

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import jakarta.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.cookie.Cookie;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletSessionTestCase {


    @BeforeClass
    public static void setup() throws ServletException {


        final PathHandler pathHandler = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addListener(new ListenerInfo(SessionCookieConfigListener.class))
                .addServlets(new ServletInfo("servlet", SessionServlet.class)
                        .addMapping("/aa/b"));
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
    public void testSimpleSessionUsage() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/aa/b");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);

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


    @Test
    public void testSessionCookieConfig() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/aa/b");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);
            String cookieValue = result.getHeaders("Set-Cookie")[0].getValue();
            Assert.assertTrue(cookieValue.contains("MySessionCookie"));
            Assert.assertTrue(cookieValue.contains("/servletContext/aa/"));

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


    @Test
    public void testSessionConfigNoCookies() throws IOException {
        TestHttpClient client = new TestHttpClient();
        client.setCookieStore(new CookieStore() {
            @Override
            public void addCookie(Cookie cookie) {

            }

            @Override
            public List<Cookie> getCookies() {
                return Collections.EMPTY_LIST;
            }

            @Override
            public boolean clearExpired(Date date) {
                return false;
            }

            @Override
            public void clear() {

            }
        });
        try {
            HttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/aa/b;foo=bar"));
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);
            String url = result.getHeaders("url")[0].getValue();

            result = client.execute(new HttpGet(url));
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            url = result.getHeaders("url")[0].getValue();
            Assert.assertEquals("2", response);

            result = client.execute(new HttpGet(url));
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testSessionConfigNoCookiesMatrixParameters() throws IOException {
        TestHttpClient client = new TestHttpClient();
        client.setCookieStore(new CookieStore() {
            @Override
            public void addCookie(Cookie cookie) {

            }

            @Override
            public List<Cookie> getCookies() {
                return Collections.EMPTY_LIST;
            }

            @Override
            public boolean clearExpired(Date date) {
                return false;
            }

            @Override
            public void clear() {

            }
        });
        try {
            HttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + ";foo=bar/servletContext/aa/b"));
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("1", response);
            String url = result.getHeaders("url")[0].getValue();

            result = client.execute(new HttpGet(url));
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            url = result.getHeaders("url")[0].getValue();
            Assert.assertEquals("2", response);

            result = client.execute(new HttpGet(url));
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("3", response);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
