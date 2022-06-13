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

import jakarta.servlet.ServletException;

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

/**
 * @author Tomaz Cerar
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
                .addServlet(Servlets.servlet("respLArget", LargeResponseWriterServlet.class)
                        .addMapping("/large"))
                .addServlet(Servlets.servlet("exception", ExceptionWriterServlet.class)
                        .addMapping("/exception"));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());
        DefaultServer.setRootHandler(root);
    }

    @Test
    public void testContentLengthBasedFlush() throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/resp?test=" + ResponseWriterServlet.CONTENT_LENGTH_FLUSH);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String data = FileUtils.readFile(result.getEntity().getContent());
            Assert.assertEquals("first-aaaa", data);
            Assert.assertEquals(0, result.getHeaders("not-header").length);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testWriterLargeResponse() throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/large");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String data = FileUtils.readFile(result.getEntity().getContent());
            Assert.assertEquals(LargeResponseWriterServlet.getMessage(), data);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testExceptionResponse() throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/exception");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = FileUtils.readFile(result.getEntity().getContent());
            MatcherAssert.assertThat(response, CoreMatchers.startsWith("java.lang.Exception: TestException"));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
