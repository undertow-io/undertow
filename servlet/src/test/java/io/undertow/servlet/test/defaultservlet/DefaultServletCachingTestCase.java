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

package io.undertow.servlet.test.defaultservlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;

import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.resource.CachingResourceManager;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.path.ServletPathMappingTestCase;
import io.undertow.servlet.test.util.PathTestServlet;
import io.undertow.servlet.test.util.MessageFilter;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.FileUtils;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.BufferAllocator;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class DefaultServletCachingTestCase {

    private static final int METADATA_MAX_AGE = 2000;
    public static final String DIR_NAME = "/cacheTest";

    static File tmpDir;

    @BeforeClass
    public static void setup() throws ServletException {

        tmpDir = new File(System.getProperty("java.io.tmpdir") + DIR_NAME);
        tmpDir.mkdirs();
        tmpDir.deleteOnExit();

        final PathHandler root = new PathHandler();
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setClassLoader(ServletPathMappingTestCase.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .setResourceManager(new CachingResourceManager(100, 10000, new DirectBufferCache(1000, 10, 1000 * 10 * 1000, BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, METADATA_MAX_AGE), new FileResourceManager(tmpDir, 10485760), METADATA_MAX_AGE));

        builder.addServlet(new ServletInfo("DefaultTestServlet", PathTestServlet.class)
                .addMapping("/path/default"))
                .addFilter(Servlets.filter("message", MessageFilter.class).addInitParam(MessageFilter.MESSAGE, "FILTER_TEXT "))
                .addFilterUrlMapping("message", "*.txt", DispatcherType.REQUEST);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        root.addPrefixPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(root);
    }

    @AfterClass
    public static void after() {
        FileUtils.deleteRecursive(tmpDir);
    }

    @Test
    public void testFileExistanceCheckCached() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        String fileName = "doesnotexist.html";
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + fileName);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);

            File f = new File(tmpDir, fileName);
            writeFile(f, "hello");
            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + fileName);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
            Thread.sleep(METADATA_MAX_AGE);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + fileName);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("hello", response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testFileContentsCached() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        String fileName = "hello.html";
        File f = new File(tmpDir, fileName);
        writeFile(f, "hello");
        try {
            for (int i = 0; i < 10; ++i) {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + fileName);
                HttpResponse result = client.execute(get);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("hello", response);
            }
            writeFile(f, "hello world");


            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + fileName);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("hello", response);

            Thread.sleep(METADATA_MAX_AGE);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + fileName);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("hello world", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testFileContentsCachedWithFilter() throws IOException, InterruptedException {
        TestHttpClient client = new TestHttpClient();
        String fileName = "hello.txt";
        File f = new File(tmpDir, fileName);
        writeFile(f, "hello");
        try {
            for (int i = 0; i < 10; ++i) {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + fileName);
                HttpResponse result = client.execute(get);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("FILTER_TEXT hello", response);
            }
            writeFile(f, "hello world");


            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + fileName);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("FILTER_TEXT hello", response);

            Thread.sleep(METADATA_MAX_AGE);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext/" + fileName);
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("FILTER_TEXT hello world", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void writeFile(final File f, final String contents) throws IOException {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(contents.getBytes());
        } finally {
            out.close();
        }
    }

}
