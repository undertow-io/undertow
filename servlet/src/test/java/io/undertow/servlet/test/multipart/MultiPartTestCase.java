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

package io.undertow.servlet.test.multipart;

import io.undertow.UndertowOptions;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.LoggingExceptionHandler;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import jakarta.servlet.ServletException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.undertow.servlet.Servlets.multipartConfig;
import static io.undertow.servlet.Servlets.servlet;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class MultiPartTestCase {


    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet((deploymentInfo, servletContext) -> {
            deploymentInfo.addListener(Servlets.listener(AddMultipartServetListener.class));
            deploymentInfo.setExceptionHandler(LoggingExceptionHandler.builder().add(RuntimeException.class, "io.undertow", Logger.Level.DEBUG).build());
        },
                servlet("mp0", MultiPartServlet.class)
                        .addMapping("/0"),
                servlet("mp1", MultiPartServlet.class)
                        .addMapping("/1")
                        .setMultipartConfig(multipartConfig(null, 0, 0, 0)),
                servlet("mp2", MultiPartServlet.class)
                        .addMapping("/2")
                        .setMultipartConfig(multipartConfig(null, 0, 3, 0)),
                servlet("mp3", MultiPartServlet.class)
                        .addMapping("/3")
                        .setMultipartConfig(multipartConfig(null, 3, 0, 0)),
                servlet("mp4", MultiPartServlet.class)
                        .addMapping("/4")
                        .setMultipartConfig(multipartConfig(null, 0, 1000000, 0)));
    }

    @Test
    public void testMultiPartRequestWithNoMultipartConfig() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext/0";
            HttpPost post = new HttpPost(uri);
            HttpEntity entity = MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.LEGACY)
                    .addTextBody("formValue", "myValue", ContentType.create("text/plain", StandardCharsets.UTF_8))
                    .addPart("file", new FileBody(new File(MultiPartTestCase.class.getResource("uploadfile.txt").getFile())))
                    .build();

            post.setEntity(entity);
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("EXCEPTION: class java.lang.IllegalStateException", response);
                return null;
            });
        }
    }

    @Test
    public void testMultiPartRequest() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext/1";
            HttpPost post = new HttpPost(uri);

            HttpEntity entity = MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.LEGACY).setBoundary(null)
                    .addPart("formValue", new StringBody("myValue", ContentType.TEXT_PLAIN))
                    .addPart("file", new FileBody(new File(MultiPartTestCase.class.getResource("uploadfile.txt").getFile())))
                    .build();

            post.setEntity(entity);
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("PARAMS:\r\n" +
                        "parameter count: 1\r\n" +
                        "parameter name count: 1\r\n" +
                        "name: formValue\r\n" +
                        "filename: null\r\n" +
                        "content-type: null\r\n" +
                        "Content-Disposition: form-data; name=\"formValue\"\r\n" +
                        "value: myValue\r\n" +
                        "size: 7\r\n" +
                        "content: myValue\r\n" +
                        "name: file\r\n" +
                        "filename: uploadfile.txt\r\n" +
                        "content-type: application/octet-stream\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"uploadfile.txt\"\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "size: 13\r\n" +
                        "content: file contents\r\n", response);
                return null;
            });
        }
    }


    @Test
    public void testMultiPartRequestWithAddedServlet() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext/added";
            HttpPost post = new HttpPost(uri);
            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.LEGACY)
                    .addPart("formValue", new StringBody("myValue", ContentType.TEXT_PLAIN))
                    .addPart("file", new FileBody(new File(MultiPartTestCase.class.getResource("uploadfile.txt").getFile())))
                    .build();

            post.setEntity(entity);
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("PARAMS:\r\n" +
                        "parameter count: 1\r\n" +
                        "parameter name count: 1\r\n" +
                        "name: formValue\r\n" +
                        "filename: null\r\n" +
                        "content-type: null\r\n" +
                        "Content-Disposition: form-data; name=\"formValue\"\r\n" +
                        "value: myValue\r\n" +
                        "size: 7\r\n" +
                        "content: myValue\r\n" +
                        "name: file\r\n" +
                        "filename: uploadfile.txt\r\n" +
                        "content-type: application/octet-stream\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"uploadfile.txt\"\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "size: 13\r\n" +
                        "content: file contents\r\n", response);
                return null;
            });
        }
    }

    @Test
    public void testMultiPartRequestTooLarge() throws IOException {
        // FIXME UNDERTOW-2572
        Assume.assumeFalse(DefaultServer.isH2());
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext/2";
            HttpPost post = new HttpPost(uri);
            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.LEGACY)
                    .addPart("formValue", new StringBody("myValue", ContentType.TEXT_PLAIN))
                    .addPart("file", new FileBody(new File(MultiPartTestCase.class.getResource("uploadfile.txt").getFile())))
                    .build();

            post.setEntity(entity);
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, result.getCode());
                return null;
            });
        } catch (IOException expected) {
            //in some environments the forced close of the read side will cause a connection reset
        }
    }

    @Test
    public void testMultiPartIndividualFileToLarge() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext/3";
            HttpPost post = new HttpPost(uri);
            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.LEGACY)

                    .addPart("formValue", new StringBody("myValue", ContentType.TEXT_PLAIN))
                    .addPart("file", new FileBody(new File(MultiPartTestCase.class.getResource("uploadfile.txt").getFile())))
                    .build();

            post.setEntity(entity);
            client.execute(post, result -> {
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("EXCEPTION: class java.lang.IllegalStateException", response);
                return null;
            });
        }
    }

    @Test
    public void testMultiPartRequestUtf8CharsetInPart() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext/1";
            HttpPost post = new HttpPost(uri);

            HttpEntity entity = MultipartEntityBuilder.create()
                    .addPart("formValue", new StringBody("myValue\u00E5", ContentType.create("text/plain", StandardCharsets.UTF_8)))
                    .build();

            post.setEntity(entity);
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);

                Assert.assertEquals("PARAMS:\r\n" +
                        "parameter count: 1\r\n" +
                        "parameter name count: 1\r\n" +
                        "name: formValue\r\n" +
                        "filename: null\r\n" +
                        "content-type: text/plain; charset=UTF-8\r\n" +
                        "Content-Disposition: form-data; name=\"formValue\"\r\n" +
                        "value: " + "myValue" + '\u00E5' + "\r\n" +
                        "Content-Type: text/plain; charset=UTF-8\r\n" +
                        "size: 9\r\n" +
                        "content: myValue\u00E5\r\n", response);
                return null;
            });
        }
    }

    @Test
    public void testMultiPartRequestBigPostForm() throws IOException {
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext/getParam";
            HttpPost post = new HttpPost(uri);

            String myValue = generateContent("myValue", 0x4000 * 2);
            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.LEGACY).setBoundary(null)
                    .addPart("formValue", new StringBody(myValue, ContentType.TEXT_PLAIN))
                    .addPart("file", new FileBody(new File(MultiPartTestCase.class.getResource("uploadfile.txt").getFile())))
                    .build();

            post.setEntity(entity);
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("PARAMS:\r\n" +
                        "parameter count: 1\r\n" +
                        "parameter name count: 1\r\n" +
                        "name: formValue\r\n" +
                        "filename: null\r\n" +
                        "content-type: null\r\n" +
                        "Content-Disposition: form-data; name=\"formValue\"\r\n" +
                        "value: " + myValue + "\r\n" +
                        "size: " + myValue.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                        "content: " + myValue + "\r\n" +
                        "name: file\r\n" +
                        "filename: uploadfile.txt\r\n" +
                        "content-type: application/octet-stream\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"uploadfile.txt\"\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "size: 13\r\n" +
                        "content: file contents\r\n" +
                        "param name: formValue\r\n" +
                        "param value: " + myValue + "\r\n", response);
                return null;
            });
        }
    }

    @Test
    @ProxyIgnore
    public void testMultiPartRequestSizeLimitOverridesServerEntitySize() throws IOException {
        OptionMap existing = DefaultServer.getUndertowOptions();
        try (CloseableHttpClient client = TestHttpClient.defaultClient()) {
            String uri = DefaultServer.getDefaultServerURL() + "/servletContext/4";
            HttpPost post = new HttpPost(uri);

            HttpEntity entity = MultipartEntityBuilder.create().setMode(HttpMultipartMode.LEGACY).setBoundary(null)
                    .addPart("formValue", new StringBody("myValue", ContentType.TEXT_PLAIN))
                    .addPart("file", new FileBody(new File(MultiPartTestCase.class.getResource("uploadfile.txt").getFile())))
                    .build();

            // This limit should not apply with the set multipart max-request-size. We surely fail if it does.
            OptionMap maxSize = OptionMap.create(UndertowOptions.MAX_ENTITY_SIZE, (long) 1);
            DefaultServer.setUndertowOptions(maxSize);

            post.setEntity(entity);
            client.execute(post, result -> {
                Assert.assertEquals(StatusCodes.OK, result.getCode());
                final String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("PARAMS:\r\n" +
                        "parameter count: 1\r\n" +
                        "parameter name count: 1\r\n" +
                        "name: formValue\r\n" +
                        "filename: null\r\n" +
                        "content-type: null\r\n" +
                        "Content-Disposition: form-data; name=\"formValue\"\r\n" +
                        "value: myValue\r\n" +
                        "size: 7\r\n" +
                        "content: myValue\r\n" +
                        "name: file\r\n" +
                        "filename: uploadfile.txt\r\n" +
                        "content-type: application/octet-stream\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"uploadfile.txt\"\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "size: 13\r\n" +
                        "content: file contents\r\n", response);
                return null;
            });
        } finally {
            DefaultServer.setUndertowOptions(existing);
        }
    }

    private String generateContent(String chunk, int size) {
        int checkLength = chunk.getBytes().length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size / checkLength; i++) {
            sb.append(chunk);
        }
        return sb.toString();
    }
}
