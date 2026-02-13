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
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.Header;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class FileHandlerTestCase {

    @Test
    public void testFileIsServed() throws IOException, URISyntaxException {
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 10485760))
                                    .setDirectoryListingEnabled(true))));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Header[] headers = result.getHeaders("Content-Type");
                Assert.assertEquals("text/html", headers[0].getValue());
                Assert.assertTrue(response, response.contains("A web page"));
                return null;
            });
        }
    }

    @Test
    public void testHeadRequest() throws IOException, URISyntaxException {
        Path file = Paths.get(getClass().getResource("page.html").toURI());
        Path rootPath = file.getParent();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 10485760))
                                    .setDirectoryListingEnabled(true))));

            HttpHead get = new HttpHead(DefaultServer.getDefaultServerURL() + "/path/page.html");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertEquals(Long.toString(Files.size(file)), result.getHeaders(Headers.CONTENT_LENGTH_STRING)[0].getValue());
                Header[] headers = result.getHeaders("Content-Type");
                Assert.assertEquals("text/html", headers[0].getValue());
                return null;
            });
        }
    }

    @Test
    public void testDirectoryListing() throws IOException, URISyntaxException {
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 1))
                                    .setDirectoryListingEnabled(true))));

            client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/path/"), result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertNotNull(result.getFirstHeader(Headers.CONTENT_TYPE_STRING));
                MatcherAssert.assertThat(result.getFirstHeader(Headers.CONTENT_TYPE_STRING).getValue(), CoreMatchers.startsWith("text/html"));
                MatcherAssert.assertThat(HttpClientUtils.readResponse(result), CoreMatchers.containsString("page.html"));
                return null;
            });
            client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/path/?js"), result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertNotNull(result.getFirstHeader(Headers.CONTENT_TYPE_STRING));
                MatcherAssert.assertThat(result.getFirstHeader(Headers.CONTENT_TYPE_STRING).getValue(), CoreMatchers.startsWith("application/javascript"));
                MatcherAssert.assertThat(HttpClientUtils.readResponse(result), CoreMatchers.containsString("growit()"));
                return null;
            });
            client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/path/?css"), result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                Assert.assertNotNull(result.getFirstHeader(Headers.CONTENT_TYPE_STRING));
                MatcherAssert.assertThat(result.getFirstHeader(Headers.CONTENT_TYPE_STRING).getValue(), CoreMatchers.startsWith("text/css"));
                MatcherAssert.assertThat(HttpClientUtils.readResponse(result), CoreMatchers.containsString("data:image/png;base64"));
                return null;
            });
        }
    }

    @Test
    public void testNoDirectoryListing() throws IOException, URISyntaxException {
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 1)))));

            try (CloseableHttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/path"))) {
                Assert.assertEquals(StatusCodes.FORBIDDEN, result.getCode());
            }
            try (CloseableHttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/path?js"))) {
                Assert.assertEquals(StatusCodes.FORBIDDEN, result.getCode());
            }
            try (CloseableHttpResponse result = client.execute(new HttpGet(DefaultServer.getDefaultServerURL() + "/path?css"))) {
                Assert.assertEquals(StatusCodes.FORBIDDEN, result.getCode());
            }
        }
    }

    @Test
    public void testDotSuffix() throws IOException, URISyntaxException {
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 1))
                                    .setDirectoryListingEnabled(true))));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html.");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.NOT_FOUND, result.getCode());
                return HttpClientUtils.readResponse(result);
            });
        }
    }

    @Test
    public void testFileTransfer() throws IOException, URISyntaxException {
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 1))
                                    // 1 byte = force transfer
                                    .setDirectoryListingEnabled(true))));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Header[] headers = result.getHeaders("Content-Type");
                Assert.assertEquals("text/html", headers[0].getValue());
                Assert.assertTrue(response, response.contains("A web page"));
                return null;
            });
        }
    }

    @Test
    public void testFileTransferLargeFile() throws IOException {
        Path tmp = Paths.get("target", "testtmp").toAbsolutePath();
        Files.createDirectories(tmp);
        String message = "Hello World".repeat(100000);
        Path large = Files.createTempFile(tmp, null, ".txt");
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
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
            client.execute(get, result -> {
                Assert.assertEquals(
                        String.format("Failed to get file '%s' with request '%s'", large.toAbsolutePath(), get),
                        StatusCodes.OK,
                        result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Header[] headers = result.getHeaders("Content-Type");
                Assert.assertEquals("text/plain", headers[0].getValue());
                Assert.assertTrue(response, response.equals(message));
                return null;
            });
        } finally {
            try {
                Files.delete(large);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testRangeRequests() throws IOException, URISyntaxException {
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PathResourceManager(rootPath, 1))
                                    // 1 byte = force transfer
                                    .setDirectoryListingEnabled(true))));

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
            get.addHeader("range", "bytes=2-3");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Header[] headers = result.getHeaders("Content-Type");
                Assert.assertEquals("text/html", headers[0].getValue());
                Assert.assertEquals("--", response);
                return null;
            });

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
            get.addHeader("range", "bytes=-7");
            client.execute(get, result -> {
                Assert.assertEquals(StatusCodes.PARTIAL_CONTENT, result.getCode());
                String response = HttpClientUtils.readResponse(result);
                Header[] headers = result.getHeaders("Content-Type");
                Assert.assertEquals("text/html", headers[0].getValue());
                Assert.assertEquals("</html>", response);
                return null;
            });
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
