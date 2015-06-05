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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.cache.CacheHandler;
import io.undertow.server.handlers.cache.DirectBufferCache;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import io.undertow.testutils.TestHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@AjpIgnore // it looks like apache actually has trouble with the number of requests
@RunWith(DefaultServer.class)
public class FileHandlerStressTestCase {


    public static final int NUM_THREADS = 10;
    public static final int NUM_REQUESTS = 100;

    @Test
    public void simpleFileStressTest() throws IOException, ExecutionException, InterruptedException, URISyntaxException {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        try {
            Path rootPath = Paths.get(getClass().getResource("page.html").toURI()).getParent();
            final ResourceHandler handler = new ResourceHandler(new PathResourceManager(rootPath, 10485760));

            final CacheHandler cacheHandler = new CacheHandler(new DirectBufferCache(1024, 10, 10480), handler);
            final PathHandler path = new PathHandler();
            path.addPrefixPath("/path", cacheHandler);
            final CanonicalPathHandler root = new CanonicalPathHandler();
            root.setNext(path);
            DefaultServer.setRootHandler(root);
            final List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < NUM_THREADS; ++i) {
                futures.add(executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        TestHttpClient client = new TestHttpClient();
                        try {
                            for (int i = 0; i < NUM_REQUESTS; ++i) {
                                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path/page.html");
                                HttpResponse result = client.execute(get);
                                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                                final String response = HttpClientUtils.readResponse(result);
                                Assert.assertTrue(response, response.contains("A web page"));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            client.getConnectionManager().shutdown();
                        }
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }

        } finally {
            executor.shutdown();
        }
    }
}

