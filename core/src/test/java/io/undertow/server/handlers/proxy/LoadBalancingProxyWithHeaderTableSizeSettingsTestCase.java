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
package io.undertow.server.handlers.proxy;

import io.undertow.Undertow;
import io.undertow.testutils.DefaultServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xnio.XnioWorker;

import static io.undertow.UndertowOptions.ENABLE_HTTP2;
import static io.undertow.server.handlers.ResponseCodeHandler.HANDLE_404;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static io.undertow.util.StatusCodes.OK;
import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.junit.Assert.assertEquals;
import static org.xnio.Options.BACKLOG;
import static org.xnio.Options.WORKER_TASK_MAX_THREADS;

/**
 * This can be noticed only when debugging. The java.net client used by this test sends a bigger than the default header table
 * size settings, and the proxy should not forward the different config to the server.
 *
 * @author Flavia Rainone
 */
public class LoadBalancingProxyWithHeaderTableSizeSettingsTestCase {
    private Undertow server;
    private Undertow loadBalancer;

    @Before
    public void setUp() throws java.io.IOException, InterruptedException {
        server = startHttpServer();
        loadBalancer = startLoadBalancer();
        server.start();
        loadBalancer.start();
    }

    @After
    public void tearDown() {
        XnioWorker worker1 = null, worker2 = null;
        int countDown = 0;
        try {
            if (loadBalancer != null) {
                final XnioWorker worker = loadBalancer.getWorker();
                loadBalancer.stop();
                // if stop did not shutdown the worker, we need to run the latch to prevent a Address already in use (UNDERTOW-1960)
                if (worker != null && !worker.isShutdown()) {
                    countDown++;
                    worker1 = worker;
                }
            }
        } finally {
            try {
                if (server != null) {
                    final XnioWorker worker = server.getWorker();
                    server.stop();
                    // if stop did not shutdown the worker, we need to run the latch to prevent a Address already in use (UNDERTOW-1960)
                    if (worker != null && !worker.isShutdown() && worker != worker1) {
                        worker2 = worker;
                        countDown ++;
                    }
                }
            } finally {
                if (countDown != 0) {
                    // TODO this is needed solely for ssl servers; replace this by the mechanism described in UNDERTOW-1648 once it is implemented
                    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(countDown);
                    if (worker1 != null) worker1.getIoThread().execute(latch::countDown);
                    if (worker2 != null) worker2.getIoThread().execute(latch::countDown);
                    try {
                        latch.await();
                        //double protection, we need to guarantee that the servers have stopped, and some environments seem to need a small delay to re-bind the socket
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }
            }
        }
    }

    private Undertow startHttpServer() {
        return Undertow.builder()
                .addHttpListener(8001, DefaultServer.isIpv6()? "::1" : "127.0.0.1")
                .setServerOption(ENABLE_HTTP2, true)
                .setHandler(exchange -> {
                    exchange.setStatusCode(OK);
                    exchange.getResponseHeaders().put(CONTENT_TYPE, "text/plain; charset=UTF-8");
                    exchange.getResponseSender().send("Hello, world!");
                })
                .build();
    }

    private Undertow startLoadBalancer() {
        final int workerThreads = Runtime.getRuntime().availableProcessors() * 8;

        final LoadBalancingProxyClient loadBalancer = new LoadBalancingProxyClient()
                .setConnectionsPerThread(20)
                .addHost(java.net.URI.create(/*DefaultServer.isIpv6()? "http://[::1]:8001/" : */"http://localhost:8001/"));

        final ProxyHandler proxyHandler = ProxyHandler.builder()
                .setReuseXForwarded(false)
                .setRewriteHostHeader(false)
                .setMaxRequestTime(30_000)
                .setProxyClient(loadBalancer)
                .setNext(HANDLE_404)
                .build();

        return Undertow.builder()
                .setIoThreads(4)
                .setWorkerThreads(workerThreads)
                .setServerOption(ENABLE_HTTP2, true)
                .setWorkerOption(WORKER_TASK_MAX_THREADS, workerThreads)
                .setSocketOption(BACKLOG, 1000)
                .setHandler(proxyHandler)
                .addHttpListener(8000, /*DefaultServer.isIpv6()? "::" : */"0.0.0.0")
                .build();
    }

    @Test
    public void sendRequest() throws java.io.IOException, InterruptedException {
        final var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(/*DefaultServer.isIpv6()? "http://[::1]:8000" : */"http://localhost:8000"))
                .GET().build();

        final var response = newHttpClient().send(request, ofString());

        assertEquals(200, response.statusCode());
        assertEquals("Hello, world!", response.body());
    }
}