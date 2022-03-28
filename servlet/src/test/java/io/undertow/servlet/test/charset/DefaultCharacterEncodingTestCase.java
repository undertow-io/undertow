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

package io.undertow.servlet.test.charset;

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Artemy Osipov
 */
@RunWith(DefaultServer.class)
public class DefaultCharacterEncodingTestCase {

    private void setup(final String defaultEncoding) throws ServletException {
        DeploymentUtils.setupServlet(new ServletExtension() {
                                         @Override
                                         public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
                                             if (defaultEncoding != null) {
                                                 deploymentInfo.setDefaultEncoding(defaultEncoding);
                                             }
                                         }
                                     },
                Servlets.servlet("servlet", DefaultCharacterEncodingServlet.class)
                        .addMapping("/"));
    }

    private void testDefaultEncoding(String defaultCharacterEncoding,
                                     String expectedRequestCharacterEncoding,
                                     String expectedResponseCharacterEncoding) throws IOException, ServletException {
        setup(defaultCharacterEncoding);
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("Unexpected request character encoding",
                    expectedRequestCharacterEncoding, readParameter(response, "requestCharacterEncoding"));
            Assert.assertEquals("Unexpected response character encoding",
                    expectedResponseCharacterEncoding, readParameter(response, "responseCharacterEncoding"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void testServletContextCharacterEncoding(final String requestCharacterEncoding, final String responseCharacterEncoding,
                                                     final String defaultContainerLevelEncoding, final String body)
            throws IOException, ServletException {
        DeploymentUtils.setupServlet(new ServletExtension() {
                                         @Override
                                         public void handleDeployment(final DeploymentInfo deploymentInfo, final ServletContext servletContext) {
                                             servletContext.setRequestCharacterEncoding(requestCharacterEncoding);
                                             servletContext.setResponseCharacterEncoding(responseCharacterEncoding);
                                         }
                                     },
                Servlets.servlet("servlet", DefaultCharacterEncodingServlet.class).addMapping("/"));
        TestHttpClient client = new TestHttpClient();
        try {
            final HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext");
            if (body != null) {
                post.setEntity(new StringEntity(body, requestCharacterEncoding));
            }
            // spec mandates "ISO-8859-1" as the default (see javadoc of ServletResponse#getCharacterEncoding())
            final String expectedResponseCharEncoding = responseCharacterEncoding == null ? "ISO-8859-1" : responseCharacterEncoding;
            final HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result, Charset.forName(expectedResponseCharEncoding));
            final String expectedRequestCharEncoding = requestCharacterEncoding == null ? "null" : requestCharacterEncoding;
            Assert.assertEquals("Unexpected request character encoding",
                    expectedRequestCharEncoding, readParameter(response, "requestCharacterEncoding"));
            Assert.assertEquals("Unexpected response character encoding",
                    expectedResponseCharEncoding, readParameter(response, "responseCharacterEncoding"));
            if (body != null) {
                Assert.assertEquals("Unexpected response body", body, readParameter(response, "content"));
            }
        } finally {
            client.getConnectionManager().shutdown();
        }

    }

    private String readParameter(String response, String parameter) {
        Pattern pattern = Pattern.compile(parameter + "=(.*?);");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    @Test
    public void testDefaultEncodingNotSet() throws IOException, ServletException {
        testDefaultEncoding(null, "null", "ISO-8859-1");
    }

    @Test
    public void testDefaultEncodingSetEqualDefault() throws IOException, ServletException {
        testDefaultEncoding("ISO-8859-1", "ISO-8859-1", "ISO-8859-1");
    }

    @Test
    public void testDefaultEncodingSetNotEqualDefault() throws IOException, ServletException {
        testDefaultEncoding("UTF-8", "UTF-8", "UTF-8");
    }

    /**
     * Tests that the character encoding set on the servlet context using {@link ServletContext#setRequestCharacterEncoding(String)}
     * and {@link ServletContext#setResponseCharacterEncoding(String)} is honoured at runtime during request/response processing
     *
     * @throws Exception
     */
    @Test
    public void testServletContextCharEncoding() throws Exception {
        final String[] defaultContainerLevelEncodings = new String[]{null, StandardCharsets.ISO_8859_1.name(),
                StandardCharsets.UTF_8.name(), StandardCharsets.UTF_16BE.name()};
        for (final String defaultContainerLevelEncoding : defaultContainerLevelEncodings) {
            testServletContextCharacterEncoding(null, null, defaultContainerLevelEncoding, null);
            testServletContextCharacterEncoding("UTF-8", null, defaultContainerLevelEncoding, null);
            testServletContextCharacterEncoding(null, "UTF-8", defaultContainerLevelEncoding, null);
            testServletContextCharacterEncoding(StandardCharsets.UTF_16BE.name(), "UTF-8", defaultContainerLevelEncoding, null);
            // send a unicode string in body
            testServletContextCharacterEncoding("UTF-8", "UTF-8", defaultContainerLevelEncoding, "\u3042");

        }
    }
}
