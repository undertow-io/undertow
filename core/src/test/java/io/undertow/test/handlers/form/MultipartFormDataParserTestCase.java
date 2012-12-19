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

package io.undertow.test.handlers.form;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;


import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.MultiPartHandler;
import io.undertow.test.utils.DefaultServer;
import io.undertow.test.utils.FileUtils;
import io.undertow.test.utils.HttpClientUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import io.undertow.util.TestHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class MultipartFormDataParserTestCase {

    @BeforeClass
    public static void setup() {
        final MultiPartHandler fd = new MultiPartHandler();
        fd.setNext(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler) {
                final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
                try {
                    FormData data = parser.parse().get();
                    exchange.setResponseCode(500);
                    if (data.getFirst("formValue").getValue().equals("myValue")) {
                        FormData.FormValue file = data.getFirst("file");
                        if (file.isFile()) {
                            if (file.getFile() != null) {
                                if (FileUtils.readFile(file.getFile()).startsWith("file contents")) {
                                    exchange.setResponseCode(200);
                                }
                            }
                        }
                    }
                    completionHandler.handleComplete();
                } catch (IOException e) {
                    exchange.setResponseCode(500);
                    completionHandler.handleComplete();
                }
            }
        });
        DefaultServer.setRootHandler(fd);
    }

    @Test
    public void testFileUpload() throws Exception {
        TestHttpClient client = new TestHttpClient();
        try {

            HttpPost post = new HttpPost(DefaultServer.getDefaultServerAddress() + "/path");
            //post.setHeader(Headers.CONTENT_TYPE, MultiPartHandler.MULTIPART_FORM_DATA);
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);

            entity.addPart("formValue", new StringBody("myValue", "text/plain", Charset.forName("UTF-8")));
            entity.addPart("file", new FileBody(new File(MultipartFormDataParserTestCase.class.getResource("uploadfile.txt").getFile())));

            post.setEntity(entity);
            HttpResponse result = client.execute(post);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(result);


        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
