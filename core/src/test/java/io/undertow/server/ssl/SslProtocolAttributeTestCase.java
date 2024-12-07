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
package io.undertow.server.ssl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;

import io.undertow.server.handlers.accesslog.AccessLogFileTestCase;
import io.undertow.server.handlers.accesslog.AccessLogHandler;
import io.undertow.server.handlers.accesslog.DefaultAccessLogReceiver;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.CompletionLatchHandler;
import io.undertow.util.StatusCodes;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DefaultServer.class)
public class SslProtocolAttributeTestCase {
    private static final Path logDirectory = Paths.get(System.getProperty("java.io.tmpdir"));

    @Test
    public void testTlsRequestViaLogging() throws IOException, InterruptedException {
        logDirectory.toFile().mkdirs();
        Path logFileName = logDirectory.resolve("server1.log");
        File logFile = logFileName.toFile();
        logFile.createNewFile();
        logFile.deleteOnExit();

        AccessLogReceiver logReceiver = new AccessLogReceiver(DefaultServer.getWorker(), logDirectory,
            "server1.", "log");

        String formatString = "SSL Protocol is %{SSL_PROTOCOL}.";
        CompletionLatchHandler latchHandler= new CompletionLatchHandler(
            new AccessLogHandler(exchange -> exchange.getResponseSender().send("ping"),
                logReceiver, formatString,
                AccessLogFileTestCase.class.getClassLoader()));
        DefaultServer.setRootHandler(latchHandler);

        try(TestHttpClient client = new TestHttpClient()) {
            DefaultServer.startSSLServer();
            SSLContext sslContext = DefaultServer.getClientSSLContext();
            client.setSSLContext(sslContext);

            HttpGet get = new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/path");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            Assert.assertEquals("ping", HttpClientUtils.readResponse(result));
            latchHandler.await();
            logReceiver.awaitWrittenForTest();
            Assert.assertEquals(formatString.replaceAll("%\\{SSL_PROTOCOL}", sslContext.getProtocol()) + System.lineSeparator(),
                new String(Files.readAllBytes(logFileName)));
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

    private static class AccessLogReceiver extends DefaultAccessLogReceiver {
        AccessLogReceiver(final Executor logWriteExecutor,
                                 final Path outputDirectory,
                                 final String logBaseName,
                                 final String logNameSuffix) {
            super(logWriteExecutor, outputDirectory, logBaseName, logNameSuffix, true);
        }

        public void awaitWrittenForTest() throws InterruptedException {
            super.awaitWrittenForTest();
        }
    }

}
