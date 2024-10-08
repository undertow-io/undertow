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

package io.undertow.server.handlers.form;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoUtils;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class MultipartFormDataParserTestCase {

    private static HttpHandler createHandler() {
        return new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final FormDataParser parser = FormParserFactory.builder().build().createParser(exchange);
                try {
                    FormData data = parser.parseBlocking();
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                    if (data.getFirst("formValue").getValue().equals("myValue")) {
                        FormData.FormValue file = data.getFirst("file");
                        if (file.isFile()) {
                            if (file.getPath() != null) {
                                if (new String(Files.readAllBytes(file.getPath())).startsWith("file contents")) {
                                    exchange.setStatusCode(StatusCodes.OK);
                                }
                            }
                        }
                    }
                    exchange.endExchange();
                } catch (Throwable e) {
                    e.printStackTrace();
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                    exchange.endExchange();
                } finally {
                    IoUtils.safeClose(parser);
                }
            }
        };
    }

    @Test
    public void testFileUpload() throws Exception {
        DefaultServer.setRootHandler(new BlockingHandler(createHandler()));
        TestHttpClient client = new TestHttpClient();
        try {

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            //post.setHeader(Headers.CONTENT_TYPE, MultiPartHandler.MULTIPART_FORM_DATA);
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);

            entity.addPart("formValue", new StringBody("myValue", "text/plain", StandardCharsets.UTF_8));
            entity.addPart("file", new FileBody(new File(MultipartFormDataParserTestCase.class.getResource("uploadfile.txt").getFile())));

            post.setEntity(entity);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testQuotedBoundary() throws Exception {
        DefaultServer.setRootHandler(new BlockingHandler(createHandler()));
        TestHttpClient client = new TestHttpClient();
        try {

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            post.setHeader(Headers.CONTENT_TYPE_STRING, "multipart/form-data; boundary=\"s58IGsuzbg6GBG1yIgUO8;n4WkVf7clWMje\"");
            StringEntity entity = new StringEntity("--s58IGsuzbg6GBG1yIgUO8;n4WkVf7clWMje\r\n" +
                    "Content-Disposition: form-data; name=\"formValue\"\r\n" +
                    "\r\n" +
                    "myValue\r\n" +
                    "--s58IGsuzbg6GBG1yIgUO8;n4WkVf7clWMje\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"uploadfile.txt\"\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "\r\n" +
                    "file contents\r\n" +
                    "\r\n" +
                    "--s58IGsuzbg6GBG1yIgUO8;n4WkVf7clWMje--\r\n");

            post.setEntity(entity);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testFileUploadWithEagerParsing() throws Exception {
        DefaultServer.setRootHandler(new EagerFormParsingHandler().setNext(createHandler()));
        TestHttpClient client = new TestHttpClient();
        try {

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            //post.setHeader(Headers.CONTENT_TYPE, MultiPartHandler.MULTIPART_FORM_DATA);
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);

            entity.addPart("formValue", new StringBody("myValue", "text/plain", StandardCharsets.UTF_8));
            entity.addPart("file", new FileBody(new File(MultipartFormDataParserTestCase.class.getResource("uploadfile.txt").getFile())));

            post.setEntity(entity);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testFileUploadWithEagerParsingAndNonASCIIFilename() throws Exception {
        DefaultServer.setRootHandler(new EagerFormParsingHandler().setNext(createHandler()));
        TestHttpClient client = new TestHttpClient();
        try {

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            MultipartEntity entity = new MultipartEntity();

            entity.addPart("formValue", new StringBody("myValue", "text/plain", StandardCharsets.UTF_8));

            File uploadfile = new File(MultipartFormDataParserTestCase.class.getResource("uploadfile.txt").getFile());
            FormBodyPart filePart = new FormBodyPart("file", new FileBody(uploadfile, "τεστ", "application/octet-stream", Charsets.UTF_8.toString()));
            filePart.addField("Content-Disposition", "form-data; name=\"file\"; filename*=\"utf-8''%CF%84%CE%B5%CF%83%CF%84.txt\"");
            entity.addPart(filePart);

            post.setEntity(entity);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private static HttpHandler createInMemoryReadingHandler(final long fileSizeThreshold) {
        return new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                MultiPartParserDefinition multiPartParserDefinition = new MultiPartParserDefinition();
                multiPartParserDefinition.setFileSizeThreshold(fileSizeThreshold);
                final FormDataParser parser = FormParserFactory.builder(false)
                        .addParsers(new FormEncodedDataDefinition(), multiPartParserDefinition)
                        .build().createParser(exchange);
                try {
                    FormData data = parser.parseBlocking();
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                    if (data.getFirst("formValue").getValue().equals("myValue")) {
                        FormData.FormValue file = data.getFirst("file");
                        if (file.isFileItem()) {
                            exchange.setStatusCode(StatusCodes.OK);
                            logResult(exchange, file.getFileItem().isInMemory(), file.getFileName(), stream2String(file));
                        }
                    }
                    exchange.endExchange();
                } catch (Throwable e) {
                    e.printStackTrace();
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                    exchange.endExchange();
                } finally {
                    IoUtils.safeClose(parser);
                }
            }

            private String stream2String(FormData.FormValue file) throws IOException {
                try (InputStream is = file.getFileItem().getInputStream()) {
                    StringWriter sw = new StringWriter();
                    IOUtils.copy(is, sw, "UTF-8");
                    return sw.toString();
                }
            }

            private String getFileName(FormData.FormValue data) {
                HeaderValues cdHeaders = data.getHeaders().get("content-disposition");
                for (String cdHeader : cdHeaders) {
                    if (cdHeader.startsWith("form-data")) {
                        return cdHeader.substring(cdHeader.indexOf("filename=") + "filename=".length()).replace("\"", "");
                    }
                }
                return null;
            }

            private void logResult(HttpServerExchange exchange, boolean inMemory, String fileName, String content) throws IOException {
                String res = String.format("in_memory:%s;file_name:%s;hash:%s", inMemory, fileName, DigestUtils.md5Hex(content));
                final OutputStream outputStream = exchange.getOutputStream();
                outputStream.write(res.getBytes());
                outputStream.close();
            }
        };
    }

    @Test
    public void testFileUploadWithSmallFileSizeThreshold() throws Exception {
        DefaultServer.setRootHandler(new BlockingHandler(createInMemoryReadingHandler(10)));

        TestHttpClient client = new TestHttpClient();
        try {

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);

            entity.addPart("formValue", new StringBody("myValue", "text/plain", StandardCharsets.UTF_8));
            entity.addPart("file", new FileBody(new File(MultipartFormDataParserTestCase.class.getResource("uploadfile.txt").getFile())));

            post.setEntity(entity);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String resp = HttpClientUtils.readResponse(result);

            Map<String, String> parsedResponse = parse(resp);

            Assert.assertEquals("false", parsedResponse.get("in_memory"));
            Assert.assertEquals("uploadfile.txt", parsedResponse.get("file_name"));
            Assert.assertEquals(DigestUtils.md5Hex(new FileInputStream(new File(MultipartFormDataParserTestCase.class.getResource("uploadfile.txt").getFile()))), parsedResponse.get("hash"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testFileUploadWithLargeFileSizeThreshold() throws Exception {
        DefaultServer.setRootHandler(new BlockingHandler(createInMemoryReadingHandler(10_000)));

        TestHttpClient client = new TestHttpClient();
        try {

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);

            entity.addPart("formValue", new StringBody("myValue", "text/plain", StandardCharsets.UTF_8));
            entity.addPart("file", new FileBody(new File(MultipartFormDataParserTestCase.class.getResource("uploadfile.txt").getFile())));

            post.setEntity(entity);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String resp = HttpClientUtils.readResponse(result);

            Map<String, String> parsedResponse = parse(resp);
            Assert.assertEquals("true", parsedResponse.get("in_memory"));
            Assert.assertEquals("uploadfile.txt", parsedResponse.get("file_name"));
            Assert.assertEquals(DigestUtils.md5Hex(new FileInputStream(new File(MultipartFormDataParserTestCase.class.getResource("uploadfile.txt").getFile()))), parsedResponse.get("hash"));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    @Test
    public void testFileUploadWithMediumFileSizeThresholdAndLargeFile() throws Exception {
        int fileSizeThreshold = 1000;
        DefaultServer.setRootHandler(new BlockingHandler(createInMemoryReadingHandler(fileSizeThreshold)));

        TestHttpClient client = new TestHttpClient();
        File file = new File("tmp_upload_file.txt");
        file.createNewFile();
        try {
            writeLargeFileContent(file, fileSizeThreshold * 2);

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL() + "/path");
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);

            entity.addPart("formValue", new StringBody("myValue", "text/plain", StandardCharsets.UTF_8));
            entity.addPart("file", new FileBody(file));

            post.setEntity(entity);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String resp = HttpClientUtils.readResponse(result);

            Map<String, String> parsedResponse = parse(resp);
            Assert.assertEquals("false", parsedResponse.get("in_memory"));
            Assert.assertEquals("tmp_upload_file.txt", parsedResponse.get("file_name"));
            Assert.assertEquals(DigestUtils.md5Hex(new FileInputStream(file)), parsedResponse.get("hash"));

        } finally {
            file.delete();
            client.getConnectionManager().shutdown();
        }
    }

    private void writeLargeFileContent(File file, int size) throws IOException {
        int textLength = "content".getBytes().length;
        FileOutputStream fos = new FileOutputStream(file);
        for (int i = 0; i < size / textLength; i++) {
            fos.write("content".getBytes());
        }
        fos.flush();
        fos.close();
    }

    private Map<String, String> parse(String resp) {
        Map<String, String> parsed = new HashMap<>();

        String[] split = resp.split(";");
        for (String s : split) {
            String[] pair = s.split(":");
            parsed.put(pair[0], pair[1]);
        }

        return parsed;
    }
}
