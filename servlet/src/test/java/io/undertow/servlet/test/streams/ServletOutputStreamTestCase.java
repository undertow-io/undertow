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

package io.undertow.servlet.test.streams;

import java.io.IOException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ServletOutputStreamTestCase {

    public static String message;

    public static final String HELLO_WORLD = "Hello World";
    public static final String BLOCKING_SERVLET = "blockingOutput";
    public static final String ASYNC_SERVLET = "asyncOutput";
    public static final String CONTENT_LENGTH_SERVLET = "contentLength";
    public static final String RESET = "reset";

    public static final String START = "START";
    public static final String END = "END";

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(new ServletExtension() {
            @Override
            public void handleDeployment(DeploymentInfo deploymentInfo, ServletContext servletContext) {
                deploymentInfo.setIgnoreFlush(false);
            }
        },
                new ServletInfo(BLOCKING_SERVLET, BlockingOutputStreamServlet.class)
                        .addMapping("/" + BLOCKING_SERVLET),
                new ServletInfo(ASYNC_SERVLET, AsyncOutputStreamServlet.class)
                        .addMapping("/" + ASYNC_SERVLET)
                        .setAsyncSupported(true),
                new ServletInfo(CONTENT_LENGTH_SERVLET, ContentLengthCloseFlushServlet.class)
                        .addMapping("/" + CONTENT_LENGTH_SERVLET),
                new ServletInfo(RESET, ResetBufferServlet.class).addMapping("/" + RESET));
    }


    @Test
    public void testFlushAndCloseWithContentLength() throws Exception {
        TestHttpClient client = createClient();
        try {
            String uri = getBaseUrl() + "/servletContext/" + CONTENT_LENGTH_SERVLET;

            HttpGet get = new HttpGet(uri);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("a", response);

            get = new HttpGet(uri);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("OK", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    protected TestHttpClient createClient() {
        return new TestHttpClient();
    }


    @Test
    public void testResetBuffer() throws Exception {
        TestHttpClient client = createClient();
        try {
            String uri = getBaseUrl() + "/servletContext/" + RESET;

            HttpGet get = new HttpGet(uri);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("hello world", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testBlockingServletOutputStream() throws IOException {
        Assume.assumeFalse(DefaultServer.isH2upgrade()); // FIXME UNDERTOW-1937 returns 503 instead of 200
        message = START +  HELLO_WORLD + END;
        runTest(message, BLOCKING_SERVLET, false, true, 1, true, false, false);

        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        builder.append(START);
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 1000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString() + END;
                runTest(message, BLOCKING_SERVLET, false, false, 1, false, false, false);
                runTest(message, BLOCKING_SERVLET, true, false, 10, false, false, false);
                runTest(message, BLOCKING_SERVLET, false, true, 3, false, false, false);
                runTest(message, BLOCKING_SERVLET, true, true, 7, false, false, false);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }


    @Test
    public void testChunkedResponseWithInitialFlush() throws IOException {
        message = START + HELLO_WORLD + END;
        runTest(message, BLOCKING_SERVLET, false, true, 1, true, false, false);
    }

    @Test
    public void testAsyncServletOutputStream() {
        Assume.assumeFalse(DefaultServer.isH2upgrade()); // FIXME UNDERTOW-1937 returns 503 instead of 200
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        builder.append(START);
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString() + END;
                runTest(message, ASYNC_SERVLET, false, false, 1, false, false, false);
                runTest(message, ASYNC_SERVLET, true, false, 10, false, false, false);
                runTest(message, ASYNC_SERVLET, false, true, 3, false, false, false);
                runTest(message, ASYNC_SERVLET, true, true, 7, false, false, false);
            } catch (Exception e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletOutputStreamOffIOThread() {
        Assume.assumeFalse(DefaultServer.isH2upgrade()); // FIXME UNDERTOW-1937 returns 503 instead of 200
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        builder.append(START);
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString() + END;
                runTest(message, ASYNC_SERVLET, false, false, 1, false, false, true);
                runTest(message, ASYNC_SERVLET, true, false, 10, false, false, true);
                runTest(message, ASYNC_SERVLET, false, true, 3, false, false, true);
                runTest(message, ASYNC_SERVLET, true, true, 7, false, false, true);
            } catch (Exception e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletOutputStreamWithPreableOffIOThread() {
        Assume.assumeFalse(DefaultServer.isH2upgrade()); // FIXME UNDERTOW-1937 returns 503 instead of 200
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        builder.append(START);
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString() + END;
                runTest(message, ASYNC_SERVLET, false, false, 1, false, true, true);
                runTest(message, ASYNC_SERVLET, true, false, 10, false, true, true);
                runTest(message, ASYNC_SERVLET, false, true, 3, false, true, true);
                runTest(message, ASYNC_SERVLET, true, true, 7, false, true, true);
            } catch (Exception e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletOutputStreamWithPreable() {
        Assume.assumeFalse(DefaultServer.isH2upgrade()); // FIXME UNDERTOW-1937 returns 503 instead of 200
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        builder.append(START);
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString() + END;
                runTest(message, ASYNC_SERVLET, false, false, 1, false, true, false);
                runTest(message, ASYNC_SERVLET, true, false, 10, false, true, false);
                runTest(message, ASYNC_SERVLET, false, true, 3, false, true, false);
                runTest(message, ASYNC_SERVLET, true, true, 7, false, true, false);
            } catch (Exception e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    public void runTest(final String message, String url, final boolean flush, final boolean close, int reps, boolean initialFlush, boolean writePreable, boolean offIoThread) throws IOException {
        TestHttpClient client = createClient();
        try {
            ServletOutputStreamTestCase.message = message;
            String uri = getBaseUrl() + "/servletContext/" + url + "?reps=" + reps + "&";
            if (flush) {
                uri = uri + "flush=true&";
            }
            if (close) {
                uri = uri + "close=true&";
            }
            if(initialFlush) {
                uri = uri + "initialFlush=true&";
            }
            if(writePreable) {
                uri = uri + "preamble=true&";
            }
            if(offIoThread) {
                uri += "offIoThread=true&";
            }
            HttpGet get = new HttpGet(uri);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            StringBuilder builder = new StringBuilder(reps * message.length());
            for (int j = 0; j < reps; ++j) {
                builder.append(message);
            }
            if(writePreable) {
                builder.append(builder.toString()); //content gets written twice in this case
            }
            final String response = HttpClientUtils.readResponse(result);
            String expected = builder.toString();
            Assert.assertTrue("Must start with START", response.startsWith(START));
            Assert.assertTrue("Must end with END", response.endsWith(END));
            Assert.assertEquals(expected.length(), response.length());
            Assert.assertEquals(expected, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    protected String getBaseUrl() {
        return DefaultServer.getDefaultServerURL();
    }

}
