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

package io.undertow.server.handlers.accesslog;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.CompletionLatchHandler;
import io.undertow.util.FileUtils;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests writing the access log to a file
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class AccessLogFileTestCase {

    private static final File logDirectory = new File(System.getProperty("java.io.tmpdir") + "/logs");

    private static final int NUM_THREADS = 10;
    private static final int NUM_REQUESTS = 12;

    @Before
    public void before() {
        logDirectory.mkdirs();
    }

    @After
    public void after() {
        FileUtils.deleteRecursive(logDirectory);
    }

    private static final HttpHandler HELLO_HANDLER = new HttpHandler() {
        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            exchange.getResponseSender().send("Hello");
        }
    };

    @Test
    public void testSingleLogMessageToFile() throws IOException, InterruptedException {
        File directory = logDirectory;
        File logFileName = new File(directory, "server1.log");
        DefaultAccessLogReceiver logReceiver = new DefaultAccessLogReceiver(DefaultServer.getWorker(), directory, "server1");
        verifySingleLogMessageToFile(logFileName, logReceiver);
    }

    @Test
    public void testSingleLogMessageToFileWithSuffix() throws IOException, InterruptedException {
        File directory = logDirectory;
        File logFileName = new File(directory, "server1.logsuffix");
        DefaultAccessLogReceiver logReceiver = new DefaultAccessLogReceiver(DefaultServer.getWorker(), directory, "server1", ".logsuffix");
        verifySingleLogMessageToFile(logFileName, logReceiver);
    }

    private void verifySingleLogMessageToFile(File logFileName, DefaultAccessLogReceiver logReceiver) throws IOException, InterruptedException {

        CompletionLatchHandler latchHandler;
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(new AccessLogHandler(HELLO_HANDLER, logReceiver, "Remote address %a Code %s test-header %{i,test-header} %{i,non-existent}", AccessLogFileTestCase.class.getClassLoader())));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("test-header", "single-val");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latchHandler.await();
            logReceiver.awaitWrittenForTest();
            Assert.assertEquals("Remote address 127.0.0.1 Code 200 test-header single-val -\n", FileUtils.readFile(logFileName));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testLogLotsOfThreads() throws IOException, InterruptedException, ExecutionException {
        File directory = logDirectory;
        File logFileName = new File(directory, "server2.log");

        DefaultAccessLogReceiver logReceiver = new DefaultAccessLogReceiver(DefaultServer.getWorker(), directory, "server2");
        CompletionLatchHandler latchHandler;
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(NUM_REQUESTS * NUM_THREADS, new AccessLogHandler(HELLO_HANDLER, logReceiver, "REQ %{i,test-header}", AccessLogFileTestCase.class.getClassLoader())));

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        try {

            final List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < NUM_THREADS; ++i) {
                final int threadNo = i;
                futures.add(executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        TestHttpClient client = new TestHttpClient();
                        try {
                            for (int i = 0; i < NUM_REQUESTS; ++i) {
                                HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
                                get.addHeader("test-header", "thread-" + threadNo + "-request-" + i);
                                HttpResponse result = client.execute(get);
                                Assert.assertEquals(200, result.getStatusLine().getStatusCode());
                                final String response = HttpClientUtils.readResponse(result);
                                Assert.assertEquals("Hello", response);
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
        latchHandler.await();
        logReceiver.awaitWrittenForTest();
        String completeLog = FileUtils.readFile(logFileName);
        for (int i = 0; i < NUM_THREADS; ++i) {
            for (int j = 0; j < NUM_REQUESTS; ++j) {
                Assert.assertTrue(completeLog.contains("REQ thread-" + i + "-request-" + j));
            }
        }

    }


    @Test
    public void testForcedLogRotation() throws IOException, InterruptedException {
        File logFileName = new File(logDirectory, "server.log");

        DefaultAccessLogReceiver logReceiver = new DefaultAccessLogReceiver(DefaultServer.getWorker(), logDirectory, "server");
        CompletionLatchHandler latchHandler;
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(new AccessLogHandler(HELLO_HANDLER, logReceiver, "Remote address %a Code %s test-header %{i,test-header}", AccessLogFileTestCase.class.getClassLoader())));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("test-header", "v1");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latchHandler.await();
            latchHandler.reset();
            logReceiver.awaitWrittenForTest();
            Assert.assertEquals("Remote address 127.0.0.1 Code 200 test-header v1\n", FileUtils.readFile(logFileName));
            logReceiver.rotate();
            logReceiver.awaitWrittenForTest();
            Assert.assertFalse(logFileName.exists());
            File firstLogRotate = new File(logDirectory, "server_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log");
            Assert.assertEquals("Remote address 127.0.0.1 Code 200 test-header v1\n", FileUtils.readFile(firstLogRotate));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("test-header", "v2");
            result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latchHandler.await();
            latchHandler.reset();
            logReceiver.awaitWrittenForTest();
            Assert.assertEquals("Remote address 127.0.0.1 Code 200 test-header v2\n", FileUtils.readFile(logFileName));
            logReceiver.rotate();
            logReceiver.awaitWrittenForTest();
            Assert.assertFalse(logFileName.exists());
            File secondLogRotate = new File(logDirectory, "server_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "-1.log");
            Assert.assertEquals("Remote address 127.0.0.1 Code 200 test-header v2\n", FileUtils.readFile(secondLogRotate));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
