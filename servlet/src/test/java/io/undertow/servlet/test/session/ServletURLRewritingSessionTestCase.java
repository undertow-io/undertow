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

import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.ServletSessionConfig;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * basic test of in memory session functionality
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletURLRewritingSessionTestCase {

    public static final String COUNT = "count";

    @BeforeClass
    public static void setup() {
        DeploymentUtils.setupServlet((deploymentInfo, servletContext) ->
                deploymentInfo.setServletSessionConfig(new ServletSessionConfig()
                        .setSessionTrackingModes(Collections.singleton(SessionTrackingMode.URL))), Servlets.servlet(URLRewritingServlet.class)
                .addMapping("/foo"));
    }

    @Test
    public void testURLRewriting() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo;foo=bar");
            String url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("0", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(url);
            url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("1", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(url);
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("2", header[0].getValue());
                return null;
            });
        }
    }

    @Test
    public void testURLRewritingWithQueryParameters() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo?a=b;c");
            String url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("0", header[0].getValue());
                Assert.assertEquals("b;c", result.getHeaders("a")[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(url);
            url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("1", header[0].getValue());
                Assert.assertEquals("b;c", result.getHeaders("a")[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(url);
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("2", header[0].getValue());
                Assert.assertEquals("b;c", result.getHeaders("a")[0].getValue());
                return null;
            });
        }
    }


    @Test
    public void testURLRewritingWithExistingOldSessionId() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo;jsessionid=foobar");
            String url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("0", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });


            get = new HttpGet(url);
            url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("1", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(url);
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("2", header[0].getValue());
                return null;
            });
        }
    }

    @Test
    public void testURLRewritingWithExistingOldSessionIdAndOtherPathParams() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo;jsessionid=foobar&a=b");
            String url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("0", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(url);
            url = client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("1", header[0].getValue());
                return HttpClientUtils.readResponse(result);
            });

            get = new HttpGet(url);
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                HttpClientUtils.readResponse(result);
                Header[] header = result.getHeaders(COUNT);
                Assert.assertEquals("2", header[0].getValue());
                return null;
            });
        }
    }

    @Test
    public void testGetRequestedSessionId() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                return HttpClientUtils.readResponse(result);
            });
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/foo;jsessionid=test");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                return HttpClientUtils.readResponse(result);
            });
        }
    }

    public static class URLRewritingServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

            String sessionIdBefore = req.getRequestedSessionId();
            HttpSession session = req.getSession(true);
            String sessionIdAfter = req.getRequestedSessionId();
            Assert.assertEquals(String.format("sessionIdBefore %s, sessionIdAfter %s", sessionIdBefore, sessionIdAfter), sessionIdBefore, sessionIdAfter);

            Object existing = session.getAttribute(COUNT);
            if (existing == null) {
                session.setAttribute(COUNT, 0);
            } else {
                Assert.assertTrue(req.getRequestURI().startsWith("/servletContext/foo;"));
                Assert.assertTrue(req.getRequestURI().contains("jsessionid=" + session.getId()));
            }
            Integer count = (Integer) session.getAttribute(COUNT);
            resp.addHeader(COUNT, count.toString());
            session.setAttribute(COUNT, ++count);

            for (Map.Entry<String, String[]> qp : req.getParameterMap().entrySet()) {
                resp.addHeader(qp.getKey(), qp.getValue()[0]);
            }
            if (req.getQueryString() == null) {
                resp.getWriter().write(resp.encodeURL(DefaultServer.getDefaultServerURL() + req.getRequestURI()));
            } else {
                resp.getWriter().write(resp.encodeURL(DefaultServer.getDefaultServerURL() + req.getRequestURI() + "?" + req.getQueryString()));
            }
        }
    }
}
