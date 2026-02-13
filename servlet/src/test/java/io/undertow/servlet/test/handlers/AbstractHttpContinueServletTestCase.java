/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.handlers;

import io.undertow.servlet.Servlets;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Stuart Douglas
 */
public abstract class AbstractHttpContinueServletTestCase {

    private static volatile boolean accept = false;

    @BeforeClass
    public static void setup() {
        DeploymentUtils.setupServlet(Servlets.servlet(ContinueConsumeServlet.class).addMappings("/path"),
                Servlets.servlet(ContinueIgnoreServlet.class).addMappings("/ignore"));
        Assume.assumeFalse(DefaultServer.isAjp());
    }

    public static void before() throws Exception {
        Assume.assumeFalse(DefaultServer.isAjp());
    }

    protected abstract String getServerAddress();

    protected abstract HttpClientBuilder getClientBuilder();

    private CloseableHttpClient getClient() {
        return getClientBuilder().setRequestExecutor(new HttpRequestExecutor(Http1Config.custom()
                .setWaitForContinueTimeout(Timeout.INFINITE)
                .build(),
                null,
                null)).build();
    }

    @Test
    public void testHttpContinueRejected() throws IOException {
        accept = false;
        String message = "My HTTP Request!";
        try (CloseableHttpClient client = getClient()) {
            HttpPost post = new HttpPost(getServerAddress() + "/servletContext/path");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new StringEntity(message));

            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.EXPECTATION_FAILED, result.getCode());
                return null;
            });
        }
    }


    @Test
    public void testHttpContinueAccepted() throws IOException {
        accept = true;
        String message = "My HTTP Request!";
        try (CloseableHttpClient client = getClient()) {
            HttpPost post = new HttpPost(getServerAddress() + "/servletContext/path");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new StringEntity(message));

            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals(message, HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }

    @Test
    public void testHttpContinueIgnored() throws IOException {
        accept = true;
        String message = "My HTTP Request!";
        try (CloseableHttpClient client = getClient()) {
            HttpPost post = new HttpPost(getServerAddress() + "/servletContext/ignore");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new StringEntity(message));

            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("", HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }

    @Test
    public void testEmptyHttpContinue() throws IOException {
        accept = true;
        String message = "My HTTP Request!";
        try (CloseableHttpClient client = getClient()) {
            HttpGet post = new HttpGet(getServerAddress() + "/servletContext/ignore");
            post.addHeader("Expect", "100-continue");

            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals("", HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }

    //UNDERTOW-162
    @Test
    public void testHttpContinueAcceptedWithChunkedRequest() throws IOException {
        accept = true;
        String message = "My HTTP Request!";
        try (CloseableHttpClient client = getClient()) {
            HttpPost post = new HttpPost(getServerAddress() + "/servletContext/path");
            post.addHeader("Expect", "100-continue");
            post.setEntity(new AbstractHttpEntity("", null, true) {
                @Override
                public void close() {
                }

                @Override
                public InputStream getContent() throws UnsupportedOperationException {
                    return new ByteArrayInputStream(message.getBytes());
                }

                @Override
                public boolean isStreaming() {
                    return false;
                }

                @Override
                public long getContentLength() {
                    return -1;
                }
            });

            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals(message, HttpClientUtils.readResponse(result));
                return null;
            });
        }
    }

    public static class ContinueConsumeServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            try {
                if (!accept) {
                    resp.setStatus(StatusCodes.EXPECTATION_FAILED);
                    return;
                }
                byte[] buffer = new byte[1024];
                final ByteArrayOutputStream b = new ByteArrayOutputStream();
                int r = 0;
                final OutputStream outputStream = resp.getOutputStream();
                final InputStream inputStream = req.getInputStream();
                while ((r = inputStream.read(buffer)) > 0) {
                    b.write(buffer, 0, r);
                }
                outputStream.write(b.toByteArray());
                outputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class ContinueIgnoreServlet extends HttpServlet {

        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        }
    }
}
