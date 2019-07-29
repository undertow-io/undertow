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

import io.undertow.Version;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.CompletionLatchHandler;
import io.undertow.util.FileUtils;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tests writing the access log to a file
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class ExtendedAccessLogFileTestCase {

    private static final Path logDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "logs");

    public static final String PATTERN = "cs-uri cs(test-header) x-O(aa) x-H(secure)";

    private DefaultAccessLogReceiver logReceiver;

    @Before
    public void before() throws IOException {
        Files.createDirectories(logDirectory);
        DefaultServer.startSSLServer();

        logReceiver = DefaultAccessLogReceiver.builder().setLogWriteExecutor(DefaultServer.getWorker())
                .setOutputDirectory(logDirectory)
                .setLogBaseName("extended.")
                .setLogFileHeaderGenerator(new ExtendedAccessLogParser.ExtendedAccessLogHeaderGenerator(PATTERN)).build();
    }

    @After
    public void after() throws IOException {
        DefaultServer.stopSSLServer();
        FileUtils.deleteRecursive(logDirectory);
        logReceiver.close();
    }

    private static final HttpHandler HELLO_HANDLER = new HttpHandler() {
        @Override
        public void handleRequest(final HttpServerExchange exchange) throws Exception {
            exchange.getResponseHeaders().put(new HttpString("aa"), "bb");
            exchange.getResponseSender().send("Hello");
        }
    };

    @Test
    public void testSingleLogMessageToFile() throws IOException, InterruptedException {
        Path logFileName = logDirectory.resolve("extended.log");
        verifySingleLogMessageToFile(logFileName, logReceiver);
    }

    private void verifySingleLogMessageToFile(Path logFileName, DefaultAccessLogReceiver logReceiver) throws IOException, InterruptedException {

        CompletionLatchHandler latchHandler;
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(new AccessLogHandler(HELLO_HANDLER, logReceiver, PATTERN, new ExtendedAccessLogParser( ExtendedAccessLogFileTestCase.class.getClassLoader()).parse(PATTERN))));
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/path");
            get.addHeader("test-header", "single-val");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latchHandler.await();
            logReceiver.awaitWrittenForTest();
            String data = new String(Files.readAllBytes(logFileName));
            String[] lines = data.split(System.lineSeparator());
            Assert.assertEquals("#Fields: " + PATTERN, lines[0]);
            Assert.assertEquals("#Version: 2.0", lines[1]);
            Assert.assertEquals("#Software: " + Version.getFullVersionString(), lines[2]);
            Assert.assertEquals("", lines[3]);
            Assert.assertEquals("/path 'single-val' 'bb' true", lines[4]);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }


}
