/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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

import io.undertow.server.HttpHandler;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.CompletionLatchHandler;
import io.undertow.util.FileUtils;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.undertow.UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL;

/**
 * Tests writing the access log to a file
 *
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
public class AccessLogFileWithUnescapedCharactersTestCase {

    private static final Path logDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "logs");

    @DefaultServer.BeforeServerStarts
    public static void setServerOptions() {
        DefaultServer.setServerOptions(OptionMap.create(ALLOW_UNESCAPED_CHARACTERS_IN_URL, true));
    }

    @DefaultServer.AfterServerStops
    public static void clearServerOptions() {
        DefaultServer.setServerOptions(OptionMap.EMPTY);
    }

    @BeforeClass // this is run after the server starts
    public static void disableProxyUnescapedCharactersInURL() {
        // disable it in proxy or else decoded URL is sent to the Undertow server
        DefaultServer.setProxyOptions(OptionMap.create(ALLOW_UNESCAPED_CHARACTERS_IN_URL, false));
    }

    @AfterClass
    public static void clearProxyOptions() {
        // disable it in proxy or else decoded URL is sent to the Undertow server
        DefaultServer.setProxyOptions(OptionMap.EMPTY);
    }

    @Before
    public void before() throws IOException {
        Files.createDirectories(logDirectory);
    }

    @After
    public void after() throws IOException {
        FileUtils.deleteRecursive(logDirectory);
    }

    private static final HttpHandler HELLO_HANDLER = exchange -> exchange.getResponseSender().send("Hello");

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
        DefaultServer.setRootHandler(latchHandler = new CompletionLatchHandler(new AccessLogHandler(HELLO_HANDLER, logReceiver,
                "%h \"%r\" %s %b", AccessLogFileWithUnescapedCharactersTestCase.class.getClassLoader())));
        try (TestHttpClient client = new TestHttpClient()) {
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/helloworld/한글이름_test.html?param=한글이름_ahoy");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("Hello", HttpClientUtils.readResponse(result));
            latchHandler.await();
            logReceiver.awaitWrittenForTest();
            String written = new String(Files.readAllBytes(logFileName), StandardCharsets.UTF_8);
            final String protocolVersion = DefaultServer.isH2()? "HTTP/2.0" : result.getProtocolVersion().toString();
            Assert.assertEquals(DefaultServer.getDefaultServerAddress().getAddress().getHostAddress() + " \"GET " + "/helloworld/한글이름_test.html?param=한글이름_ahoy " + protocolVersion + "\" 200 5" + System.lineSeparator(), written);
        }
    }
}
