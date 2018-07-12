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
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.FileUtils;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.DecompressingEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ContentEncodedResourceTestCase {

    public static final String DIR_NAME = "contentEncodingTestCase";

    static Path tmpDir;


    @BeforeClass
    public static void setup() throws IOException{

        tmpDir = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), DIR_NAME);

        final PathResourceManager resourceManager = new PathResourceManager(tmpDir, 10485760);
        DefaultServer.setRootHandler(new ResourceHandler(resourceManager)
                .setContentEncodedResourceManager(
                        new ContentEncodedResourceManager(tmpDir, new CachingResourceManager(100, 10000, null, resourceManager, -1), new ContentEncodingRepository()
                                .addEncodingHandler("deflate", new DeflateEncodingProvider(), 50, null), 0, 100000, null)));
    }

    @AfterClass
    public static void after() throws IOException {
        FileUtils.deleteRecursive(tmpDir);
    }

    @Test
    public void testFileIsCompressed() throws IOException, InterruptedException {
        String fileName = "hello.html";
        Path f = tmpDir.resolve(fileName);
        Files.write(f, "hello world".getBytes());
        try (CloseableHttpClient client = HttpClientBuilder.create().build()){
            for (int i = 0; i < 3; ++i) {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/" + fileName);
                CloseableHttpResponse result = client.execute(get);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                String response = HttpClientUtils.readResponse(result);
                Assert.assertEquals("hello world", response);
                assert result.getEntity() instanceof DecompressingEntity; //no other nice way to be sure we get back gzipped content
                result.close();
            }
            Files.write(f, "modified file".getBytes());

            //if it is serving a cached compressed version what is being served will not change
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/" + fileName);
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals("hello world", response);
            assert result.getEntity() instanceof DecompressingEntity; //no other nice way to be sure we get back gzipped content


        }
    }
}
