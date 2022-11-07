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

package io.undertow.servlet.test.streams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Assert;
import org.junit.Test;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
public abstract class AbstractServletInputStreamTestCase {

    public static final String HELLO_WORLD = "Hello World";
    public static final String BLOCKING_SERVLET = "blockingInput";
    public static final String ASYNC_SERVLET = "asyncInput";
    public static final String ASYNC_EAGER_SERVLET = "asyncEagerInput";
    private static final int CONCURRENCY = Math.min(20, 4 * Runtime.getRuntime().availableProcessors());

    @Test
    public void testBlockingServletInputStream() {
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 1000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, BLOCKING_SERVLET, false, false);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletInputStream() {
        //for(int h = 0; h < 20 ; ++h) {
        StringBuilder builder = new StringBuilder(1000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, ASYNC_SERVLET, false, false);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
        //}
    }

    @Test
    public void testAsyncServletInputStreamWithPreamble() {
        StringBuilder builder = new StringBuilder(2000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, ASYNC_SERVLET, true, false);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletInputStreamInParallel() throws Exception {
        StringBuilder builder = new StringBuilder(100000 * HELLO_WORLD.length());
        for (int j = 0; j < 100000; ++j) {
            builder.append(HELLO_WORLD);
        }
        String message = builder.toString();
        runTestParallel(CONCURRENCY, message, ASYNC_SERVLET, false, false);
    }

    @Test
    public void testAsyncServletInputStreamInParallelOffIoThread() throws Exception {
        StringBuilder builder = new StringBuilder(100000 * HELLO_WORLD.length());
        for (int j = 0; j < 100000; ++j) {
            builder.append(HELLO_WORLD);
        }
        String message = builder.toString();
        runTestParallel(CONCURRENCY, message, ASYNC_SERVLET, false, true);
    }

    @Test
    public void testAsyncServletInputStreamOffIoThread() {
        StringBuilder builder = new StringBuilder(2000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, ASYNC_SERVLET, false, true);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletInputStreamOffIoThreadWithPreamble() {
        StringBuilder builder = new StringBuilder(2000 * HELLO_WORLD.length());
        for (int i = 0; i < 10; ++i) {
            try {
                for (int j = 0; j < 10000; ++j) {
                    builder.append(HELLO_WORLD);
                }
                String message = builder.toString();
                runTest(message, ASYNC_SERVLET, true, true);
            } catch (Throwable e) {
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }

    @Test
    public void testAsyncServletInputStreamWithEmptyRequestBody() {
        String message = "";
        try {
            runTest(message, ASYNC_SERVLET, false, false);
        } catch (Throwable e) {
            throw new RuntimeException("test failed", e);
        }
    }

    private void runTestViaJavaImpl(final String message, String url)
            throws IOException {
        HttpURLConnection urlcon = null;
        try {
            String uri = getBaseUrl() + "/servletContext/" + url;
            urlcon = (HttpURLConnection) new URL(uri).openConnection();
            urlcon.setInstanceFollowRedirects(true);
            urlcon.setRequestProperty("Connection", "close");
            urlcon.setRequestMethod("POST");
            urlcon.setDoInput(true);
            urlcon.setDoOutput(true);
            OutputStream os = urlcon.getOutputStream();
            os.write(message.getBytes());
            os.close();
            Assert.assertEquals(StatusCodes.OK, urlcon.getResponseCode());
            InputStream is = urlcon.getInputStream();

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int len;
            while ((len = is.read(buf)) > 0) {
                bytes.write(buf, 0, len);
            }
            is.close();
            final String response = new String(bytes.toByteArray(), 0, bytes.size());
            if (!message.equals(response)) {
                System.out.println(String.format("response=%s", Hex.encodeHexString(response.getBytes())));
            }
            Assert.assertEquals(message, response);
        } finally {
            if (urlcon != null) {
                urlcon.disconnect();
            }
        }
    }

    protected String getBaseUrl() {
        return DefaultServer.getDefaultServerURL();
    }

    @Test
    public void testAsyncServletInputStream3() {
        String message = "to_user_id=7999&msg_body=msg3";
        for (int i = 0; i < 200; ++i) {
            try {
                runTestViaJavaImpl(message, ASYNC_SERVLET);
            } catch (Throwable e) {
                System.out.println("test failed with i equal to " + i);
                e.printStackTrace();
                throw new RuntimeException("test failed with i equal to " + i, e);
            }
        }
    }


    public void runTest(final String message, String url, boolean preamble, boolean offIOThread) throws IOException {
        TestHttpClient client = createClient();
        try {
            String uri = getBaseUrl() + "/servletContext/" + url;
            HttpPost post = new HttpPost(uri);
            if (preamble && !message.isEmpty()) {
                post.addHeader("preamble", Integer.toString(message.length() / 2));
            }
            if (offIOThread) {
                post.addHeader("offIoThread", "true");
            }
            post.setEntity(new StringEntity(message));
            HttpResponse result = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            final String response = HttpClientUtils.readResponse(result);
            Assert.assertEquals(message.length(), response.length());
            Assert.assertEquals(message, response);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    public void runTestParallel(int concurrency, final String message, String url, boolean preamble, boolean offIOThread) throws Exception {

        CloseableHttpClient client = HttpClients.custom()
                .setMaxConnPerRoute(1000)
                .setSSLContext(DefaultServer.createClientSslContext())
                .build();
        byte[] messageBytes = message.getBytes();
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
            Callable task = new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    String uri = getBaseUrl() + "/servletContext/" + url;
                    HttpPost post = new HttpPost(uri);
                    if (preamble && !message.isEmpty()) {
                        post.addHeader("preamble", Integer.toString(message.length() / 2));
                    }
                    if (offIOThread) {
                        post.addHeader("offIoThread", "true");
                    }
                    post.setEntity(new InputStreamEntity(
                            // Server should wait for events from the client
                            new RateLimitedInputStream(new ByteArrayInputStream(messageBytes))));
                    CloseableHttpResponse result = client.execute(post);
                    try {
                        Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                        final String response = HttpClientUtils.readResponse(result);
                        Assert.assertEquals(message.length(), response.length());
                        Assert.assertEquals(message, response);
                    } finally {
                        result.close();
                    }
                    return null;
                }
            };
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < concurrency * 5; i++) {
                Future<?> future = executorService.submit(task);
                results.add(future);
            }
            for(Future<?> i : results) {
                i.get();
            }
            executorService.shutdown();
            Assert.assertTrue(executorService.awaitTermination(70, TimeUnit.SECONDS));
        } finally {
            client.close();
        }
    }

    private static final class RateLimitedInputStream extends InputStream {
        private final InputStream in;
        private int count;

        RateLimitedInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            if (count++ % 1000 == 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }
            }
            return in.read();
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    protected TestHttpClient createClient() {
        return new TestHttpClient();
    }

}
