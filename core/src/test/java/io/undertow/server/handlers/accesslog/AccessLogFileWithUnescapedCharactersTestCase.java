/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.CompletionLatchHandler;
import io.undertow.util.FileUtils;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tests writing the access log to a file
 *
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
public class AccessLogFileWithUnescapedCharactersTestCase {

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
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(new AccessLogHandler(HELLO_HANDLER, logReceiver, "%h \"%r\" %s %b"/*"Remote address %a Code %s test-header %{i,test-header} %{i,non-existent} %{i,dup}"*/, AccessLogFileWithUnescapedCharactersTestCase.class.getClassLoader())));
        //old = DefaultServer.getUndertowOptions();
        DefaultServer.setUndertowOptions(
                OptionMap.create(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, true));
        DefaultServer.setServerOptions(OptionMap.create(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, true));
        TestHttpClient client = new TestHttpClient();
        try {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/helloworld/한글이름_test.html?param=한글이름_ahoy");
            get.addHeader("test-header", "single-val");
            get.addHeader("dup", "d"); //we can't rely on ordering, so we just send the same thing twice to make the comparison easy
            get.addHeader("dup", "d");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latchHandler.await();
            logReceiver.awaitWrittenForTest();
            String written = new String(Files.readAllBytes(logFileName));
            System.out.println("Look, this is written:\n" + written);
            final String protocolVersion = DefaultServer.isH2()? "HTTP/2.0" : result.getProtocolVersion().toString();
            Assert.assertEquals(DefaultServer.getDefaultServerAddress().getAddress().getHostAddress() + " \"GET " + "/helloworld/한글이름_test.html?param=한글이름_ahoy " + protocolVersion + "\" 200 5" + System.lineSeparator(), written);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
