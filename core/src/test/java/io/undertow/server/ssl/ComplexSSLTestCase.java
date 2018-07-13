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

package io.undertow.server.ssl;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.CanonicalPathHandler;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.error.SimpleErrorPageHandler;
import io.undertow.server.handlers.file.FileHandlerTestCase;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.testutils.AjpIgnore;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import io.undertow.util.StatusCodes;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author Stuart Douglas
 */
@AjpIgnore
@RunWith(DefaultServer.class)
public class ComplexSSLTestCase {

    private static final String MESSAGE = "My HTTP Request!";

    private static volatile byte[] message;

    @Test
    public void complexSSLTestCase() throws IOException, GeneralSecurityException, URISyntaxException, InterruptedException {
        final PathHandler pathHandler = new PathHandler();
        Path rootPath = Paths.get(FileHandlerTestCase.class.getResource("page.html").toURI()).getParent();

        final NameVirtualHostHandler virtualHostHandler = new NameVirtualHostHandler();
        HttpHandler root = virtualHostHandler;
        root = new SimpleErrorPageHandler(root);
        root = new CanonicalPathHandler(root);

        virtualHostHandler.addHost("default-host", pathHandler);
        virtualHostHandler.setDefaultHandler(pathHandler);

        pathHandler.addPrefixPath("/", new ResourceHandler(new PathResourceManager(rootPath, 10485760))
                .setDirectoryListingEnabled(true));

        DefaultServer.setRootHandler(root);

        DefaultServer.startSSLServer();
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            //get file list, this works
            HttpGet getFileList = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            HttpResponse resultList = client.execute(getFileList);
            Assert.assertEquals(StatusCodes.OK, resultList.getStatusLine().getStatusCode());
            String responseList = HttpClientUtils.readResponse(resultList);
            Assert.assertTrue(responseList, responseList.contains("page.html"));
            Header[] headersList = resultList.getHeaders("Content-Type");
            Assert.assertEquals("text/html; charset=UTF-8", headersList[0].getValue());

            //get file itself, breaks
            HttpGet getFile = new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/page.html");
            HttpResponse result = client.execute(getFile);
            Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
            String response = HttpClientUtils.readResponse(result);
            Header[] headers = result.getHeaders("Content-Type");
            Assert.assertEquals("text/html", headers[0].getValue());
            Assert.assertTrue(response, response.contains("A web page"));


        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    @Test
    public void complexSSLTestCaseConcurrent() throws IOException, GeneralSecurityException, URISyntaxException, InterruptedException {
        final PathHandler pathHandler = new PathHandler();
        Path rootPath = Paths.get(FileHandlerTestCase.class.getResource("page.html").toURI()).getParent();

        final NameVirtualHostHandler virtualHostHandler = new NameVirtualHostHandler();
        HttpHandler root = virtualHostHandler;
        root = new SimpleErrorPageHandler(root);
        root = new CanonicalPathHandler(root);

        virtualHostHandler.addHost("default-host", pathHandler);
        virtualHostHandler.setDefaultHandler(pathHandler);

        pathHandler.addPrefixPath("/", new ResourceHandler(new PathResourceManager(rootPath, 10485760))
                .setDirectoryListingEnabled(true));

        runConcurrentTest(32, 1000, root, client -> {
            //get file list, this works
            HttpGet getFileList = new HttpGet(DefaultServer.getDefaultServerSSLAddress());
            try (CloseableHttpResponse resultList = client.execute(getFileList)) {
                Assert.assertEquals(StatusCodes.OK, resultList.getStatusLine().getStatusCode());
                String responseList = HttpClientUtils.readResponse(resultList);
                Assert.assertTrue(responseList, responseList.contains("page.html"));
                Header[] headersList = resultList.getHeaders("Content-Type");
                Assert.assertEquals("text/html; charset=UTF-8", headersList[0].getValue());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            //get file itself, breaks
            HttpGet getFile = new HttpGet(DefaultServer.getDefaultServerSSLAddress() + "/page.html");
            try (CloseableHttpResponse result = client.execute(getFile)) {
                Assert.assertEquals(StatusCodes.OK, result.getStatusLine().getStatusCode());
                String response = HttpClientUtils.readResponse(result);
                Header[] headers = result.getHeaders("Content-Type");
                Assert.assertEquals("text/html", headers[0].getValue());
                Assert.assertTrue(response, response.contains("A web page"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testSslLotsOfData() throws IOException, GeneralSecurityException, URISyntaxException {

        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                if(exchange.isInIoThread()) {
                    exchange.dispatch(this);
                    return;
                }
                exchange.startBlocking();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[100];
                int res = 0;
                while ((res = exchange.getInputStream().read(buf)) > 0) {
                    out.write(buf, 0, res);
                }
                System.out.println("WRITE " + out.size());
                exchange.getOutputStream().write(out.toByteArray());
                System.out.println("DONE " + out.size());
            }
        });

        DefaultServer.startSSLServer();
        TestHttpClient client = new TestHttpClient();
        client.setSSLContext(DefaultServer.getClientSSLContext());
        try {
            generateMessage(1000000);
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerSSLAddress());
            post.setEntity(new ByteArrayEntity(message));
            HttpResponse resultList = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, resultList.getStatusLine().getStatusCode());
            checkStream(resultList.getEntity().getContent());

            generateMessage(100000);
            post = new HttpPost(DefaultServer.getDefaultServerSSLAddress());
            post.setEntity(new ByteArrayEntity(message));
            resultList = client.execute(post);
            Assert.assertEquals(StatusCodes.OK, resultList.getStatusLine().getStatusCode());
            checkStream(resultList.getEntity().getContent());


        } finally {
            client.getConnectionManager().shutdown();
            DefaultServer.stopSSLServer();
        }
    }

    private void checkStream(InputStream in) throws IOException {
        for (byte b : message) {
            Assert.assertEquals(b, (byte) in.read());
        }
        Assert.assertEquals(-1, in.read());
    }

    private static void generateMessage(int repetitions) {
        final StringBuilder builder = new StringBuilder(repetitions * MESSAGE.length());
        for (int i = 0; i < repetitions; ++i) {
            builder.append(MESSAGE);
        }
        message = builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void testSslLotsOfDataConcurrent() throws Exception {
        // Uses 1/10 entity size of the singular test
        generateMessage(100000);
        runConcurrentTest(32, 50, new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                if(exchange.isInIoThread()) {
                    exchange.dispatch(this);
                    return;
                }
                exchange.startBlocking();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[100];
                int res = 0;
                while ((res = exchange.getInputStream().read(buf)) > 0) {
                    out.write(buf, 0, res);
                }
                exchange.getOutputStream().write(out.toByteArray());
            }
        }, client -> {
            HttpPost post = new HttpPost(DefaultServer.getDefaultServerSSLAddress());
            post.setEntity(new ByteArrayEntity(message));
            try (CloseableHttpResponse resultList = client.execute(post)) {
                Assert.assertEquals(StatusCodes.OK, resultList.getStatusLine().getStatusCode());
                checkStream(resultList.getEntity().getContent());
                EntityUtils.consumeQuietly(resultList.getEntity());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void runConcurrentTest(int concurrency, int multiplier, HttpHandler handler, Consumer<CloseableHttpClient> test) throws IOException {
        DefaultServer.setRootHandler(handler);
        DefaultServer.startSSLServer();
        try (CloseableHttpClient client = HttpClients.custom().disableConnectionState()
                .setSSLContext(DefaultServer.getClientSSLContext())
                .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(5000).build())
                .setMaxConnPerRoute(concurrency * 2)
                .setMaxConnTotal(concurrency * 2)
                .build()) {
            ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
            AtomicBoolean failed = new AtomicBoolean();
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    if (failed.get()) {
                        return;
                    }
                    try {
                        test.accept(client);
                    } catch (Throwable t) {
                        if (failed.compareAndSet(false, true)) {
                            t.printStackTrace();
                            executorService.shutdownNow();
                        }
                    }
                }
            };
            for (int i = 0; i < concurrency * multiplier; i++) {
                executorService.submit(task);
            }
            executorService.shutdown();
            Assert.assertTrue(executorService.awaitTermination(5, TimeUnit.MINUTES));
            Assert.assertFalse(failed.get());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            DefaultServer.stopSSLServer();
        }
    }
}
