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
import java.nio.charset.Charset;
import java.nio.file.Files;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoUtils;

/**
 * Based on the {@link MultipartFormDataParserTestCase} but with unicode content in the form parameter value.
 *
 * @author Jens Fendler
 */
@RunWith(DefaultServer.class)
public class MultipartFormDataParserUnicodeTestCase {

    private static final String UNICODE_TEST_VALUE = "-—ÄÖÜäöüß€—-";

    private static HttpHandler createHandler() {
        return new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                final FormDataParser parser = FormParserFactory.builder().build().createParser(exchange);
                try {
                    FormData data = parser.parseBlocking();
                    exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                    String formValue = data.getFirst("formValue").getValue();
                    if (formValue.equals(UNICODE_TEST_VALUE)) {
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

            entity.addPart("formValue", new StringBody(UNICODE_TEST_VALUE, "text/plain", Charset.forName("UTF-8")));
            entity.addPart("file", new FileBody(new File(MultipartFormDataParserUnicodeTestCase.class.getResource("uploadfile.txt").getFile())));

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
            post.setHeader(Headers.CONTENT_TYPE_STRING,"multipart/form-data; boundary=\"s58IGsuzbg6GBG1yIgUO8;n4WkVf7clWMje\"");
            StringEntity entity = new StringEntity("--s58IGsuzbg6GBG1yIgUO8;n4WkVf7clWMje\r\n" +
                    "Content-Disposition: form-data; name=\"formValue\"\r\n" +
                    "\r\n" +
                    UNICODE_TEST_VALUE + "\r\n" +
                    "--s58IGsuzbg6GBG1yIgUO8;n4WkVf7clWMje\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"uploadfile.txt\"\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "\r\n" +
                    "file contents\r\n" +
                    "\r\n" +
                    "--s58IGsuzbg6GBG1yIgUO8;n4WkVf7clWMje--\r\n", ContentType.create("application/octet-stream", "UTF-8"));

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

            entity.addPart("formValue", new StringBody(UNICODE_TEST_VALUE, "text/plain", Charset.forName("UTF-8")));
            entity.addPart("file", new FileBody(new File(MultipartFormDataParserUnicodeTestCase.class.getResource("uploadfile.txt").getFile())));

            post.setEntity(entity);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
