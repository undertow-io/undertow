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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.CompletionLatchHandler;
import io.undertow.util.FileUtils;

/**
 * Tests writing the access log to a file
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class AccessLogFileTestCase {

    private static final Path logDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "logs");

    private static final int NUM_THREADS = 10;
    private static final int NUM_REQUESTS = 12;

    @Before
    public void before() throws IOException {
        Files.createDirectories(logDirectory);
    }

    @After
    public void after() throws IOException {
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
        Path directory = logDirectory;
        Path logFileName = directory.resolve("server1.log");
        DefaultAccessLogReceiver logReceiver = new DefaultAccessLogReceiver(DefaultServer.getWorker(), directory, "server1.");
        verifySingleLogMessageToFile(logFileName, logReceiver);
    }

    @Test
    public void testSingleLogMessageToFileWithSuffix() throws IOException, InterruptedException {
        Path directory = logDirectory;
        Path logFileName = directory.resolve("server1.logsuffix");
        DefaultAccessLogReceiver logReceiver = new DefaultAccessLogReceiver(DefaultServer.getWorker(), directory, "server1.", "logsuffix");
        verifySingleLogMessageToFile(logFileName, logReceiver);
    }

    private void verifySingleLogMessageToFile(Path logFileName, DefaultAccessLogReceiver logReceiver) throws IOException, InterruptedException {

        CompletionLatchHandler latchHandler;
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(new AccessLogHandler(HELLO_HANDLER, logReceiver, "Remote address %a Code %s test-header %{i,test-header} %{i,non-existent} %{i,dup}", AccessLogFileTestCase.class.getClassLoader())));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("test-header", "single-val");
            get.addHeader("dup", "d"); //we can't rely on ordering, so we just send the same thing twice to make the comparison easy
            get.addHeader("dup", "d");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latchHandler.await();
            logReceiver.awaitWrittenForTest();
            Assert.assertEquals("Remote address " + DefaultServer.getDefaultServerAddress().getAddress().getHostAddress() + " Code 200 test-header single-val - [d, d]" + System.lineSeparator(), new String(Files.readAllBytes(logFileName)));
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


    @Test
    public void testLogLotsOfThreads() throws IOException, InterruptedException, ExecutionException {
        Path directory = logDirectory;
        Path logFileName = directory.resolve("server2.log");

        DefaultAccessLogReceiver logReceiver = new DefaultAccessLogReceiver(DefaultServer.getWorker(), directory, "server2.");
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
                                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
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
        String completeLog = new String(Files.readAllBytes(logFileName));
        for (int i = 0; i < NUM_THREADS; ++i) {
            for (int j = 0; j < NUM_REQUESTS; ++j) {
                Assert.assertTrue(completeLog.contains("REQ thread-" + i + "-request-" + j));
            }
        }

    }


    @Test
    public void testForcedLogRotation() throws IOException, InterruptedException {
        Path logFileName = logDirectory.resolve("server.log");

        DefaultAccessLogReceiver logReceiver = new DefaultAccessLogReceiver(DefaultServer.getWorker(), logDirectory, "server.");
        CompletionLatchHandler latchHandler;
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(new AccessLogHandler(HELLO_HANDLER, logReceiver, "Remote address %a Code %s test-header %{i,test-header}", AccessLogFileTestCase.class.getClassLoader())));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("test-header", "v1");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latchHandler.await();
            latchHandler.reset();
            logReceiver.awaitWrittenForTest();
            Assert.assertEquals("Remote address " + DefaultServer.getDefaultServerAddress().getAddress().getHostAddress() + " Code 200 test-header v1" + System.lineSeparator(), new String(Files.readAllBytes(logFileName)));
            logReceiver.rotate();
            logReceiver.awaitWrittenForTest();
            Assert.assertFalse(Files.exists(logFileName));
            Path firstLogRotate = logDirectory.resolve("server." + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log");
            Assert.assertEquals("Remote address " + DefaultServer.getDefaultServerAddress().getAddress().getHostAddress() + " Code 200 test-header v1" + System.lineSeparator(), new String(Files.readAllBytes(firstLogRotate)));

            get = new HttpGet(DefaultServer.getDefaultServerURL() + "/path");
            get.addHeader("test-header", "v2");
            result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latchHandler.await();
            latchHandler.reset();
            logReceiver.awaitWrittenForTest();
            Assert.assertEquals("Remote address " + DefaultServer.getDefaultServerAddress().getAddress().getHostAddress() + " Code 200 test-header v2" + System.lineSeparator(), new String(Files.readAllBytes(logFileName)));
            logReceiver.rotate();
            logReceiver.awaitWrittenForTest();
            Assert.assertFalse(Files.exists(logFileName));
            Path secondLogRotate = logDirectory.resolve("server." + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "-1.log");
            Assert.assertEquals("Remote address " + DefaultServer.getDefaultServerAddress().getAddress().getHostAddress() + " Code 200 test-header v2" + System.lineSeparator(), new String(Files.readAllBytes(secondLogRotate)));

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
