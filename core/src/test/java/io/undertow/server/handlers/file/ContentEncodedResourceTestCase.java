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

import io.undertow.server.handlers.encoding.ContentEncodedResourceManager;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.DeflateEncodingProvider;
import io.undertow.server.handlers.resource.CachingResourceManager;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.ContentEncodingHttpClient;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ContentEncodedResourceTestCase {

    public static final String DIR_NAME = "/contentEncodingTestCase";

    static File tmpDir;


    @BeforeClass
    public static void setup() {

        tmpDir = new File(System.getProperty("java.io.tmpdir") + DIR_NAME);
        tmpDir.mkdirs();
        tmpDir.deleteOnExit();

        final FileResourceManager resourceManager = new FileResourceManager(tmpDir, 10485760);
        DefaultServer.setRootHandler(new ResourceHandler().setResourceManager(resourceManager)
                .setContentEncodedResourceManager(
                        new ContentEncodedResourceManager(tmpDir, new CachingResourceManager(100, 10000, null, resourceManager, -1), new ContentEncodingRepository()
                                .addEncodingHandler("deflate", new DeflateEncodingProvider(), 50, null), 0, 100000, null)));
    }

    @AfterClass
    public static void after() {
        for (File file : tmpDir.listFiles()) {
            file.delete();
        }
        tmpDir.delete();
    }

    @Test
    public void testFileIsCompressed() throws IOException, InterruptedException {
        ContentEncodingHttpClient client = new ContentEncodingHttpClient();
        String fileName = "hello.html";
        File f = new File(tmpDir, fileName);
        writeFile(f, "hello world");
        try {
            for (int i = 0; i < 3; ++i) {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/" + fileName);
                HttpResponse result = client.execute(get);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("hello world", response);
                Assert.assertEquals("deflate", result.getHeaders(Headers.CONTENT_ENCODING_STRING)[0].getValue());
            }
            writeFile(f, "modified file");

            //if it is serving a cached compressed version what is being served will not change
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/" + fileName);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("hello world", response);
            Assert.assertEquals("deflate", result.getHeaders(Headers.CONTENT_ENCODING_STRING)[0].getValue());

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
