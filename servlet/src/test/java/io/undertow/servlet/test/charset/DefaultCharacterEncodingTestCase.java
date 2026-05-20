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

import io.undertow.servlet.Servlets;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

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

    private void setup(final Charset defaultEncoding) throws ServletException {
        DeploymentUtils.setupServlet((deploymentInfo, servletContext) -> {
                    if (defaultEncoding != null) {
                        deploymentInfo.setDefaultEncoding(defaultEncoding.name());
                    }
                },
                Servlets.servlet("servlet", DefaultCharacterEncodingServlet.class)
                        .addMapping("/"));
    }

    private void testDefaultEncoding(Charset defaultCharacterEncoding,
                                     String expectedRequestCharacterEncoding,
                                     String expectedResponseCharacterEncoding) throws IOException, ServletException {
        setup(defaultCharacterEncoding);
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("Unexpected request character encoding",
                        expectedRequestCharacterEncoding, readParameter(response, "requestCharacterEncoding"));
                Assert.assertEquals("Unexpected response character encoding",
                        expectedResponseCharacterEncoding, readParameter(response, "responseCharacterEncoding"));
                return null;
            });
        }
    }

    private void testServletContextCharacterEncoding(final Charset requestCharacterEncoding, final Charset responseCharacterEncoding,
                                                     final Charset defaultContainerLevelEncoding, final String body)
            throws IOException, ServletException {
        DeploymentUtils.setupServlet((deploymentInfo, servletContext) -> {
            if (defaultContainerLevelEncoding != null) {
                deploymentInfo.setDefaultEncoding(defaultContainerLevelEncoding.name());
            }
            if (requestCharacterEncoding != null) {
                servletContext.setRequestCharacterEncoding(requestCharacterEncoding);
            }
            if (responseCharacterEncoding != null) {
                servletContext.setResponseCharacterEncoding(responseCharacterEncoding);
            }
        },
                Servlets.servlet("servlet", DefaultCharacterEncodingServlet.class).addMapping("/"));
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            final HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext");
            if (body != null) {
                post.setEntity(new StringEntity(body, requestCharacterEncoding));
            }
            // spec mandates "ISO-8859-1" as the default (see javadoc of ServletResponse#getCharacterEncoding())
            final Charset expectedResponseCharEncoding = responseCharacterEncoding == null ? (defaultContainerLevelEncoding == null ? StandardCharsets.ISO_8859_1 : defaultContainerLevelEncoding) : responseCharacterEncoding;
            final String expectedRequestCharEncodingName = requestCharacterEncoding == null ? (defaultContainerLevelEncoding == null ? "null" : defaultContainerLevelEncoding.name()) : requestCharacterEncoding.name();
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                String response = HttpClientUtils.readResponse(result, expectedResponseCharEncoding);
                Assert.assertEquals("Unexpected request character encoding",
                        expectedRequestCharEncodingName, readParameter(response, "requestCharacterEncoding"));
                Assert.assertEquals("Unexpected response character encoding",
                        expectedResponseCharEncoding.name(), readParameter(response, "responseCharacterEncoding"));
                if (body != null) {
                    Assert.assertEquals("Unexpected response body", body, readParameter(response, "content"));
                }
                return null;
            });
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
        testDefaultEncoding(null, "null", StandardCharsets.ISO_8859_1.name());
    }

    @Test
    public void testDefaultEncodingSetEqualDefault() throws IOException, ServletException {
        testDefaultEncoding(StandardCharsets.ISO_8859_1, StandardCharsets.ISO_8859_1.name(), StandardCharsets.ISO_8859_1.name());
    }

    @Test
    public void testDefaultEncodingSetNotEqualDefault() throws IOException, ServletException {
        testDefaultEncoding(StandardCharsets.UTF_8, StandardCharsets.UTF_8.name(), StandardCharsets.UTF_8.name());
    }

    /**
     * Tests that the character encoding set on the servlet context using {@link ServletContext#setRequestCharacterEncoding(String)}
     * and {@link ServletContext#setResponseCharacterEncoding(String)} is honoured at runtime during request/response processing
     *
     * @throws Exception
     */
    @Test
    public void testServletContextCharEncoding() throws Exception {
        final Charset[] defaultContainerLevelEncodings = new Charset[]{null, StandardCharsets.ISO_8859_1,
                StandardCharsets.UTF_8, StandardCharsets.UTF_16BE};
        for (final Charset defaultContainerLevelEncoding : defaultContainerLevelEncodings) {
            testServletContextCharacterEncoding(null, null, defaultContainerLevelEncoding, null);
            testServletContextCharacterEncoding(StandardCharsets.UTF_8, null, defaultContainerLevelEncoding, null);
            testServletContextCharacterEncoding(null, StandardCharsets.UTF_8, defaultContainerLevelEncoding, null);
            testServletContextCharacterEncoding(StandardCharsets.UTF_16BE, StandardCharsets.UTF_8, defaultContainerLevelEncoding, null);
            // send a unicode string in body
            testServletContextCharacterEncoding(StandardCharsets.UTF_8, StandardCharsets.UTF_8, defaultContainerLevelEncoding, "\u3042");
        }
    }
}
