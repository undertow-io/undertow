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
package io.undertow.servlet.test.redirect;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.undertow.servlet.Servlets.servlet;
import static org.junit.Assert.assertEquals;

/**
 * Test redirection after setting content length (see UNDERTOW-2070)
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
public class RedirectWithContentLengthTestCase {
    @BeforeClass
    public static void setup() throws jakarta.servlet.ServletException {
        final PathHandler pathHandler = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(RedirectWithContentLengthTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setClassIntrospecter(io.undertow.servlet.test.util.TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .addServlets(
                        servlet("redirect", RedirectWithContentLengthServlet.class)
                                .addMapping("/redirect/*"));
        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        pathHandler.addPrefixPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(pathHandler);
    }

    @Test
    public void testServletRedirect() throws Exception {
        final String requestURL = DefaultServer.getDefaultServerURL() + "/servletContext/redirect/";
        final String expectedBody = "/servletContext/redirect/subpath";
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(requestURL);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            assertEquals(expectedBody, response);
            Header[] header = result.getHeaders(Headers.CONTENT_LENGTH_STRING);
            assertEquals(1, header.length);
            assertEquals(expectedBody.length(), Integer.valueOf(header[0].getValue()).intValue());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testServletRedirectNoFollow() throws Exception {
        final String requestURL = DefaultServer.getDefaultServerURL() + "/servletContext/redirect/";
        final String expectedRedirect = DefaultServer.getDefaultServerURL() + "/servletContext/redirect/subpath";
        try (CloseableHttpClient client = HttpClientBuilder.create().disableRedirectHandling().build()) {
            HttpGet get = new HttpGet(requestURL);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.FOUND, result.getStatusLine().getStatusCode());
            assertEquals("", HttpClientUtils.readResponse(result));
            Header[] header = result.getHeaders(Headers.CONTENT_LENGTH_STRING);
            assertEquals(1, header.length);
            assertEquals(0, Integer.parseInt(header[0].getValue()));
            header = result.getHeaders(Headers.LOCATION_STRING);
            assertEquals(1, header.length);
            assertEquals(expectedRedirect, header[0].getValue());
        }
    }

    public void testServletRedirectHead() throws Exception {
        final String requestURL = DefaultServer.getDefaultServerURL() + "/servletContext/redirect/";
        final String expectedBody = "/servletContext/redirect/subpath";
        TestHttpClient client = new TestHttpClient();
        try {
            HttpHead head = new HttpHead(requestURL);
            HttpResponse result = client.execute(head);
            assertEquals("", HttpClientUtils.readResponse(result));
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Header[] header = result.getHeaders(Headers.CONTENT_LENGTH_STRING);
            assertEquals(1, header.length);
            assertEquals(expectedBody.length(), Integer.parseInt(header[0].getValue()));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testServletRedirectHeadNoFollow() throws Exception {
        final String requestURL = DefaultServer.getDefaultServerURL() + "/servletContext/redirect/";
        final String expectedRedirect = DefaultServer.getDefaultServerURL() + "/servletContext/redirect/subpath";
        try (CloseableHttpClient client = HttpClientBuilder.create().disableRedirectHandling().build()) {
            HttpHead head = new HttpHead(requestURL);
            HttpResponse result = client.execute(head);
            assertEquals(StatusCodes.FOUND, result.getStatusLine().getStatusCode());
            assertEquals("", HttpClientUtils.readResponse(result));
            Header[] header = result.getHeaders(Headers.CONTENT_LENGTH_STRING);
            assertEquals(1, header.length);
            assertEquals(0, Integer.parseInt(header[0].getValue()));
            header = result.getHeaders(Headers.LOCATION_STRING);
            assertEquals(1, header.length);
            assertEquals(expectedRedirect, header[0].getValue());
        }
    }
}
