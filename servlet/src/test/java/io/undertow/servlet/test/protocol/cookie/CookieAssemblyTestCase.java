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

package io.undertow.servlet.test.protocol.cookie;

import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tests that the correct encoding is selected
 *
 * @author baranowb
 */
@RunWith(DefaultServer.class)
public class CookieAssemblyTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        ServletInfo s = new ServletInfo("cookie-eater", CookieMuncher.class)
                .addMapping("/");

        DeploymentInfo builder = new DeploymentInfo()
                .setContextPath("/")
                .setClassLoader(CookieAssemblyTestCase.class.getClassLoader())
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("cookie-test1.war")
                .addServlet(s);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);

    }

    private static class CookieMuncher extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

            final Enumeration<String> it = req.getHeaders(Headers.COOKIE_STRING);
            int cookieHeadersCount = 0;
            final StringBuffer sb = new StringBuffer();
            while (it.hasMoreElements()) {
                cookieHeadersCount++;
                sb.append(it.nextElement()).append("\n");
            }
            if (cookieHeadersCount != 1) {
                resp.setStatus(500);
                resp.setContentType("text/plain");
                resp.getWriter().write("Too many cookie headers[" + cookieHeadersCount + "]: " + sb);
                resp.getWriter().flush();
                return;
            }

            final Cookie[] cookies = req.getCookies();
            if (cookies.length != 3) {
                resp.setStatus(500);
                resp.setContentType("text/plain");
                resp.getWriter().write("Wrong number of Cookies[" + cookies.length + "]: " + Arrays.toString(cookies));
                resp.getWriter().flush();
                return;
            }

            resp.setStatus(200);
            resp.getWriter().flush();
        }
    }

    @Test
    public void testBasicEncodingSelect() throws IOException {

        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet request = new HttpGet(DefaultServer.getDefaultServerURL() + "/cookie-test1");
            request.addHeader(new BasicHeader("Cookie", "t1=munch"));
            request.addHeader(new BasicHeader("Cookie", "t2=nom"));
            request.addHeader(new BasicHeader("Cookie", "t3=gone"));
            final CloseableHttpResponse response = client.execute(request);
            final StatusLine status = response.getStatusLine();
            if (status.getStatusCode() != 200) {
                Assert.fail(new BasicResponseHandler().handleResponse(response));
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
