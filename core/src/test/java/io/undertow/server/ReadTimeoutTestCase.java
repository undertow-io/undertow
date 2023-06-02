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

package io.undertow.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketException;
import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormEncodedDataDefinition;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.Headers;
import io.undertow.util.StringWriteChannelListener;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.message.BasicNameValuePair;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.ChannelListeners;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.channels.ReadTimeoutException;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 *
 * Tests read timeout with a slow request
 *
 * @author Stuart Douglas
 * @author Flavia Rainone
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
public class ReadTimeoutTestCase {

    private static final Logger LOG = Logger.getLogger(ReadTimeoutTestCase.class);
    private static final int FORM_PARAM_LENGTH = 1024 * 16;

    private volatile Exception exception;

    @DefaultServer.BeforeServerStarts
    public static void beforeClass() {
        DefaultServer.setServerOptions(OptionMap.create(Options.READ_TIMEOUT, 10));
    }

    @DefaultServer.AfterServerStops
    public static void afterClass() {
        DefaultServer.setServerOptions(OptionMap.EMPTY);
    }

    @Test
    public void testReadTimeout() throws InterruptedException, IOException {
        final CountDownLatch errorLatch = new CountDownLatch(1);
        DefaultServer.setRootHandler((final HttpServerExchange exchange) -> {
                final StreamSinkChannel response = exchange.getResponseChannel();
                final StreamSourceChannel request = exchange.getRequestChannel();

                request.getReadSetter().set(ChannelListeners.drainListener(Long.MAX_VALUE, (final Channel channel) -> {
                                new StringWriteChannelListener("COMPLETED") {
                                    @Override
                                    protected void writeDone(final StreamSinkChannel channel) {
                                        exchange.endExchange();
                                    }
                                }.setup(response);
                        }, (final StreamSourceChannel channel, final IOException e) -> {
                                e.printStackTrace();
                                exchange.endExchange();
                                exception = e;
                                errorLatch.countDown();
                        }
                ));
                request.wakeupReads();
            });

        final TestHttpClient client = new TestHttpClient();
        try {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL());
            post.setEntity(new AbstractHttpEntity() {

                @Override
                public InputStream getContent() throws IllegalStateException {
                    return null;
                }

                @Override
                public void writeTo(final OutputStream outstream) throws IOException {
                    for (int i = 0; i < 5; ++i) {
                        outstream.write('*');
                        outstream.flush();
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                @Override
                public boolean isStreaming() {
                    return true;
                }

                @Override
                public boolean isRepeatable() {
                    return false;
                }

                @Override
                public long getContentLength() {
                    return 5;
                }
            });
            post.addHeader(Headers.CONNECTION_STRING, "close");
            boolean socketFailure = false;
            try {
                client.execute(post);
            } catch (SocketException e) {
                Assert.assertTrue(e.getMessage(), e.getMessage().contains("Broken pipe")
                        || e.getMessage().contains("connection abort") || e.getMessage().contains("connection was aborted"));
                socketFailure = true;
            }
            Assert.assertTrue("Test sent request without any exception", socketFailure);
            if (errorLatch.await(5, TimeUnit.SECONDS)) {
                Assert.assertTrue(getExceptionDescription(exception), exception instanceof ReadTimeoutException ||
                        (DefaultServer.isProxy() && exception instanceof IOException));
                if (exception.getSuppressed() != null && exception.getSuppressed().length > 0) {
                    for (Throwable supressed : exception.getSuppressed()) {
                        Assert.assertEquals(getExceptionDescription(supressed), ReadTimeoutException.class, exception.getClass());
                    }
                }
            } else if (!DefaultServer.isProxy()) {
                // ignore if proxy, because when we're on proxy, we might not be able to see the exception
                Assert.fail("Did not get ReadTimeoutException");
            }
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    /**
     * This verifies that read-timeout is cleaned-up after request is completely parsed. I.e. slow request processing
     * happening after the request has been fully read should not trigger read-timeout.
     */
    @Test
    public void testReadTimeoutWithSlowResponder() throws IOException {
        DefaultServer.setRootHandler(new BlockingHandler((final HttpServerExchange exchange) -> {
            FormParserFactory parserFactory = new FormParserFactory.Builder()
                    .addParser(new FormEncodedDataDefinition())
                    .build();

            // Reads and parses the request.
            try (FormDataParser formDataParser = parserFactory.createParser(exchange)) {
                FormData formData = formDataParser.parseBlocking();
                FormData.FormValue test = formData.getFirst("test");
                Assert.assertNotNull(test);
                Assert.assertEquals(FORM_PARAM_LENGTH, test.getValue().length());
            }

            // Write some response with delays, to breach read-timeout if it hasn't been cleaned-up.
            Sender responseSender = exchange.getResponseSender();
            for (int i = 0; i < 5; i++) {
                Thread.sleep(200);
                responseSender.send("*", new IoCallback() {
                    @Override
                    public void onComplete(HttpServerExchange exchange, Sender sender) {
                        LOG.debug("Sent '*'");
                    }

                    @Override
                    public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                        LOG.warn("Failed to write data to response channel.", exception);
                    }
                });
            }
        }));

        try (TestHttpClient client = new TestHttpClient()) {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerURL());

            // Generate 2 KB form param value.
            List<NameValuePair> nameValuePairs = new ArrayList<>();
            StringBuilder sb = new StringBuilder(FORM_PARAM_LENGTH);
            for (int i = 0; i < FORM_PARAM_LENGTH; i++) {
                sb.append('a');
            }
            nameValuePairs.add(new BasicNameValuePair("test", sb.toString()));
            post.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            // Request should succeed.
            client.execute(post);
        } catch (NoHttpResponseException e) {
            Assert.fail("No response was received, this was presumably caused by read-timeout closing the connection.");
        }
    }

    // TODO move this to an utility class
    private String getExceptionDescription(Throwable exception) {
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw)) {
            exception.printStackTrace(pw);
            return pw.toString();
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }
}
