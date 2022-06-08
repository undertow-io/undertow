/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.test.response.writer;

import javax.servlet.ServletException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FileUtils;
import io.undertow.util.StatusCodes;
import javax.servlet.ServletException;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.wildfly.common.Assert.assertTrue;

/**
 * Tests for response writer servlets.
 *
 * @author Tomaz Cerar
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
public class ResponseWriterTestCase {

    @BeforeClass
    public static void setup() throws ServletException {
        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ResponseWriterTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .addServlet(Servlets.servlet("resp", ResponseWriterServlet.class)
                        .addMapping("/resp"))
                .addServlet(Servlets.servlet("respLarge", LargeResponseWriterServlet.class)
                        .addMapping("/large"))
                .addServlet(Servlets.servlet("exception", ExceptionWriterServlet.class)
                        .addMapping("/exception"))
                .addServlet(Servlets.servlet("respBeforeRead", ResponseWriterOnPostServlet.class)
                        .addMapping("/resp-before-read"))
                .addServlet(Servlets.servlet("asyncResp", AsyncResponseWriterServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/async-resp"))
                .addServlet(Servlets.servlet("asyncRespLarge", AsyncLargeResponseWriterServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/async-large"))
                .addServlet(Servlets.servlet("asyncException", AsyncExceptionWriterServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/async-exception"))
                .addServlet(Servlets.servlet("asyncRespBeforeRead", AsyncResponseWriterOnPostServlet.class)
                        .setAsyncSupported(true)
                        .addMapping("/async-resp-before-read"))
                .addServlet(Servlets.servlet("exception", ExceptionWriterServlet.class)
                        .addMapping("/exception"));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testContentLengthBasedFlush() throws Exception {
        assertContentLengthBasedFlush("resp");
    }

    @Test
    public void testAsyncContentLengthBasedFlush() throws Exception {
        assertContentLengthBasedFlush("async-resp");
    }

    private void assertContentLengthBasedFlush(String path) throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + path + "?test=" + ResponseWriterServlet.CONTENT_LENGTH_FLUSH);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String data = FileUtils.readFile(result.getEntity().getContent());
            assertEquals("first-aaaa", data);
            assertEquals(0, result.getHeaders("not-header").length);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testWriterLargeResponse() throws Exception {
        assertWriterLargeResponse("large");
    }

    @Test
    public void testAsyncWriterLargeResponse() throws Exception {
        assertWriterLargeResponse("async-large");
    }

    private void assertWriterLargeResponse(String path) throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + path);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String data = FileUtils.readFile(result.getEntity().getContent());
            assertEquals(LargeResponseWriterServlet.getMessage(), data);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testExceptionResponse() throws Exception {
        assertExceptionResponse("exception");
    }

    @Test
    public void testAsyncExceptionResponse() throws Exception {
        assertExceptionResponse("async-exception");
    }

    private void assertExceptionResponse(String path) throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + path);
            HttpResponse result = client.execute(get);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = FileUtils.readFile(result.getEntity().getContent());
            MatcherAssert.assertThat(response, CoreMatchers.startsWith("java.lang.Exception: TestException"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testRespondBeforeRead() throws Throwable {
        assertRespondBeforeRead("resp-before-read");
    }

    @Test
    public void testAsyncRespondBeforeRead() throws Throwable {
        assertRespondBeforeRead("async-resp-before-read");
    }

    private void assertRespondBeforeRead(String path) throws Throwable {
        final List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(HttpHeaders.CONNECTION, "close"));
        final HttpClientBuilder builder = HttpClients.custom().setDefaultHeaders(headers)
                .setConnectionReuseStrategy(new NoConnectionReuseStrategy());
        try (CloseableHttpClient client = builder.build()) {
            final HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/servletContext/" + path + "?test=" + ResponseWriterServlet.CONTENT_LENGTH_FLUSH);
            // anything will do, send the bytecodes of this class just for testing purposes
            final Path rootPath = Paths.get(getClass().getResource(getClass().getSimpleName() + ".class").toURI());
            final SlowInputStream inputStream = new SlowInputStream(new BufferedInputStream(new FileInputStream(rootPath.toFile())));
            post.setEntity(new InputStreamEntity(inputStream));
            final HttpResponse result = client.execute(post);
            // wait til it is fully read
            boolean fullyRead = inputStream.waitTillIsFullyRead();
            // check if servlet ran without any exceptions
            final Throwable exception = ResponseWriterOnPostServlet.getExceptionIfAny();
            if (exception != null) {
                throw exception;
            }
            assertTrue(fullyRead);
            assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String data = FileUtils.readFile(result.getEntity().getContent());
            assertEquals("first-aaaa", data);
            assertEquals(0, result.getHeaders("not-header").length);
        }
    }

    private static class SlowInputStream extends InputStream {
        private final InputStream innerInputStream;
        final CountDownLatch latch = new CountDownLatch(1);

        SlowInputStream(InputStream innerInputStream) {
            this.innerInputStream = innerInputStream;
        }

        @Override // enforce that reading will take place after the response is written with some extra delay
        public int read() throws IOException {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int readByte = innerInputStream.read();
            if (readByte == -1) {
                latch.countDown();
            }
            return readByte;
        }

        boolean waitTillIsFullyRead() throws InterruptedException {
            boolean fullyRead = latch.await(60, TimeUnit.SECONDS);
            // I tested this and the extra sleep time is necessary to be able to view any exception caught
            // by the servlet, since the exception might happen after read above has returned -1
            Thread.sleep(50);
            return fullyRead;
        }
    }
}
