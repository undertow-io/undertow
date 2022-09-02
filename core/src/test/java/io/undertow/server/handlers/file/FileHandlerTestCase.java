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

package io.undertow.server.handlers.file;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class FileHandlerTestCase {


    @Test
    public void testFileIsServed() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 10485760))
                                    .setDirectoryListingEnabled(true))));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertTrue(response, response.contains("A web page"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testHeadRequest() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path file = Paths.get(getClass().getResource("page.html").toURI());
        Path rootPath = file.getParent();
        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 10485760))
                                    .setDirectoryListingEnabled(true))));

            HttpHead get = new HttpHead(DefaultServer.getDefaultServerURL() + "/path/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals(Long.toString(Files.size(file)), result.getHeaders(Headers.CONTENT_LENGTH_STRING)[0].getValue());
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testDotSuffix() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 1))
                                    .setDirectoryListingEnabled(true))));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html.");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.NOT_FOUND, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
    @Test
    public void testFileTransfer() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 1))
                                    // 1 byte = force transfer
                                    .setDirectoryListingEnabled(true))));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertTrue(response, response.contains("A web page"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testFileTransferLargeFile() throws IOException {
        TestHttpClient client = new TestHttpClient();
        Path tmp = Paths.get("target", "testtmp").toAbsolutePath();
        Files.createDirectories(tmp);
        String message = "Hello World".repeat(100000);
        Path large = Files.createTempFile(tmp, null, ".txt");
        try {
            Files.write(
                    large,
                    message.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(tmp, 1))
                                    // 1 byte = force transfer
                                    .setDirectoryListingEnabled(true))));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/" + large.getFileName().toString());
            HttpResponse result = client.execute(get);
            Assert.assertEquals(
                    String.format("Failed to get file '%s' with request '%s'", large.toAbsolutePath(), get),
                    StatusCodes.OK,
                    result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/plain", headers[0].getValue());
            Assert.assertTrue(response, response.equals(message));
        } finally {
            client.getConnectionManager().shutdown();
            try {
                Files.delete(large);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testRangeRequests() throws IOException, URISyntaxException {
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 1))
                                    // 1 byte = force transfer
                                    .setDirectoryListingEnabled(true))));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
            get.addHeader("range", "bytes=2-3");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertEquals("--", response);

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
            get.addHeader("range", "bytes=-7");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getStatusLine().getStatusCode());
            response = HttpClientUtils.readResponse(result);
            headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertEquals("</html>", response);

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    /*
    Starts simple file server, it is useful for testing directory browsing
     */
    public static void main(String[] args) throws URISyntaxException {
        Path rootPath = Paths.get(FileHandlerTestCase.class.getResource("page.html").toURI()).getParent().getParent();
        HttpHandler root = new CanonicalPathHandler()
                .setNext(new PathHandler()
                        .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 1))
                                // 1 byte = force transfer
                                .setDirectoryListingEnabled(true)));
        Undertow undertow = Undertow.builder()
                .addHttpListener(8888, "localhost")
                .setHandler(root)
                .build();
        undertow.start();
    }

}
