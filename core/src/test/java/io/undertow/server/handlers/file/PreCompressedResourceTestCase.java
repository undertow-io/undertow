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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.DecompressingEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.undertow.predicate.Predicates;
import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.PreCompressedResourceSupplier;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class PreCompressedResourceTestCase {

    @After
    public void clean() throws IOException, URISyntaxException {
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();

        if (Files.exists(rootPath.resolve("page.html.gz"))) {
            Files.delete(rootPath.resolve("page.html.gz"));
        }

        if (Files.exists(rootPath.resolve("page.html.gzip"))) {
            Files.delete(rootPath.resolve("page.html.gzip"));
        }

        if (Files.exists(rootPath.resolve("page.html.nonsense"))) {
            Files.delete(rootPath.resolve("page.html.nonsense"));
        }

        if (Files.exists(rootPath.resolve("page.html.gzip.nonsense"))) {
            Files.delete(rootPath.resolve("page.html.gzip.nonsense"));
        }
    }

    @Test
    public void testContentEncodedResource() throws IOException, URISyntaxException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();

        try (CloseableHttpClient compClient = HttpClientBuilder.create().build()){
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PreCompressedResourceSupplier(new PathResourceManager(rootPath, 10485760)).addEncoding("gzip", ".gz"))
                                    .setDirectoryListingEnabled(true))));

            //assert response without compression
            final String plainResponse = assertResponse(client.execute(get), false);

            //assert compressed response, that doesn't exists, so returns plain
            assertResponse(compClient.execute(get), false, plainResponse);

            //generate compressed resource with extension .gz
            generatePreCompressedResource("gz");

            //assert compressed response that was pre compressed
            assertResponse(compClient.execute(get), true, plainResponse, "gz", "text/html");

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testContentEncodedJsonResource() throws IOException, URISyntaxException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/data1.json");
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("data1.json").toURI()).getParent();

        try (CloseableHttpClient compClient = HttpClientBuilder.create().build()){
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PreCompressedResourceSupplier(new PathResourceManager(rootPath, 10485760)).addEncoding("gzip", ".gz"))
                                    .setDirectoryListingEnabled(true))));

            //assert response without compression
            final String plainResponse = assertResponse(client.execute(get), false, null, "web", "application/json");

            //assert compressed response, that doesn't exists, so returns plain
            assertResponse(compClient.execute(get), false, plainResponse, "web", "application/json");

            //generate compressed resource with extension .gz
            Path json = rootPath.resolve("data1.json");
            generateGZipFile(json, rootPath.resolve("data1.json.gz"));

            //assert compressed response that was pre compressed
            assertResponse(compClient.execute(get), true, plainResponse, "gz", "application/json");

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testContentEncodedJsonResourceWithoutUncompressed() throws IOException, URISyntaxException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/data3.json");
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("data2.json").toURI()).getParent();

        try (CloseableHttpClient compClient = HttpClientBuilder.create().build()){
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new ResourceHandler(new PreCompressedResourceSupplier(new PathResourceManager(rootPath, 10485760)).addEncoding("gzip", ".gz"))
                                    .setDirectoryListingEnabled(true))));

            //generate compressed resource with extension .gz and delete the uncompressed
            Path json = rootPath.resolve("data2.json");
            Path jsonFileToBeZippedAndDeleted = rootPath.resolve("data3.json");
            Files.copy(json, jsonFileToBeZippedAndDeleted);
            // data3.json.gz has no corresponding data3.json in the filesystem (UNDERTOW-1950)
            generateGZipFile(jsonFileToBeZippedAndDeleted, rootPath.resolve("data3.json.gz"));
            Files.delete(jsonFileToBeZippedAndDeleted);

            //assert compressed response even with missing uncompressed
            assertResponse(compClient.execute(get), true, null, "gz", "application/json");

        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testCorrectResourceSelected() throws IOException, URISyntaxException {
        HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
        TestHttpClient client = new TestHttpClient();
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();

        try (CloseableHttpClient compClient = HttpClientBuilder.create().build()){
            DefaultServer.setRootHandler(new CanonicalPathHandler()
                    .setNext(new PathHandler()
                            .addPrefixPath("/path", new EncodingHandler(new ContentEncodingRepository()
                                            .addEncodingHandler("gzip", new GzipEncodingProvider(), 50, Predicates.truePredicate()))
                                    .setNext(new ResourceHandler(new PreCompressedResourceSupplier(new PathResourceManager(rootPath, 10485760)).addEncoding("gzip", ".gzip"))
                                            .setDirectoryListingEnabled(true)))
                    ));

            //assert response without compression
            final String plainResponse = assertResponse(client.execute(get), false);

            //assert compressed response generated by filter
            assertResponse(compClient.execute(get), true, plainResponse);

            //generate resources
            generatePreCompressedResource("gzip");
            generatePreCompressedResource("nonsense");
            generatePreCompressedResource("gzip.nonsense");

            //assert compressed response that was pre compressed
            assertResponse(compClient.execute(get), true, plainResponse, "gzip", "text/html");

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private void generateGZipFile(Path source, Path target) throws IOException {
        byte[] buffer = new byte[1024];

        GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(target.toFile()));
        FileInputStream in = new FileInputStream(source.toFile());

        int len;
        while ((len = in.read(buffer)) > 0) {
            gzos.write(buffer, 0, len);
        }

        in.close();
        gzos.finish();
        gzos.close();
    }

    private void replaceStringInFile(Path file, String original, String replacement) throws IOException {
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        content = content.replaceAll(original, replacement);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private String assertResponse(HttpResponse response, boolean encoding) throws IOException {
        return assertResponse(response, encoding, null, null, "text/html");
    }

    private String assertResponse(HttpResponse response, boolean encoding, String compareWith) throws IOException {
        return assertResponse(response, encoding, compareWith, "web", "text/html");
    }

    /**
     * Series of assertions checking response code, headers and response content
     */
    private String assertResponse(HttpResponse response, boolean encoding, String compareWith, String extension, String contentType) throws IOException {
        Assert.assertEquals(StatusCodes.OK, response.getStatusLine().getStatusCode());
        String body = HttpClientUtils.readResponse(response);
        Header[] headers = response.getHeaders(Headers.CONTENT_TYPE_STRING);
        Assert.assertEquals(contentType, headers[0].getValue());

        if (encoding) {
            assert response.getEntity() instanceof DecompressingEntity; //no other nice way to be sure we get back gzipped content
        } else {
            Assert.assertNull(response.getFirstHeader(Headers.CONTENT_ENCODING_STRING));
        }

        if (compareWith != null) {
            Assert.assertEquals(compareWith.replace("\r", "").replace("web", extension), body.replace("\r", "")); //ignore line ending differences and change inside of html page
        }
        return body;
    }

    /**
     * Creates compressed resource made by compressing page.html which content is updated before with {@param extension}
     * and after compression returned to original content
     */
    private void generatePreCompressedResource(String extension) throws IOException, URISyntaxException{
        Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
        Path html = rootPath.resolve("page.html");

        replaceStringInFile(html, "web", extension);
        generateGZipFile(html, rootPath.resolve("page.html." + extension));
        replaceStringInFile(html, extension, "web");
    }
}
