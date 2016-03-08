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

package io.undertow.servlet.test.request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.servlet.ServletException;

import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ExecutorPerServletTestCase {

    private ExecutorService executorService;

    public static final int NUM_THREADS = 10;
    public static final int NUM_REQUESTS = 100;

    @Before
    public void setup() throws ServletException {
        DeploymentUtils.setupServlet(
                new ServletInfo("racey", RaceyAddServlet.class)
                        .addMapping("/racey"),
                new ServletInfo("single", RaceyAddServlet.class)
                        .addMapping("/single")
                        .setExecutor(executorService = Executors.newSingleThreadExecutor()));
    }

    @After
    public void after() {
        executorService.shutdown();
    }

    @Test
    @Ignore("This won't pass every run, but on most machines it should pass consistently")
    public void testRaceyServlet() throws InterruptedException, ExecutionException, IOException {
        Assert.assertNotEquals(NUM_REQUESTS * NUM_THREADS, runTest("/racey"));
    }

    @Test
    public void testSingleThreadExecutor() throws InterruptedException, ExecutionException, IOException {
        Assert.assertEquals(NUM_REQUESTS * NUM_THREADS, runTest("/single"));
    }

    public int runTest(final String path) throws IOException, ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        try {

            final List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < NUM_THREADS; ++i) {
                futures.add(executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        TestHttpClient client = new TestHttpClient();
                        try {
                            for (int i = 0; i < NUM_REQUESTS; ++i) {
                                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext" + path);
                                HttpResponse result = client.execute(get);
                                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                                final String response = HttpClientUtils.readResponse(result);
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
            TestHttpClient client = new TestHttpClient();
            try {
                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/servletContext" + path);
                HttpResponse result = client.execute(get);
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                return Integer.parseInt(HttpClientUtils.readResponse(result));
            } finally {
                client.getConnectionManager().shutdown();
            }
        } finally {
            executor.shutdown();
        }
    }
}
