/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.test.async;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.undertow.servlet.spec.AsyncContextImpl;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.test.util.DeploymentUtils;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpClientUtils;
import io.undertow.testutils.TestHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.undertow.servlet.Servlets.servlet;

@RunWith(DefaultServer.class)
public class AsyncCompleteConcurrencyTestCase {

    private static final BlockingQueue<CompletionResult> RESULTS = new LinkedBlockingQueue<>();

    @BeforeClass
    public static void setup() throws ServletException {
        DeploymentUtils.setupServlet(servlet("complete", CompleteServlet.class)
                .setAsyncSupported(true).addMapping("/complete"),
                servlet("target", MessageServlet.class).setAsyncSupported(true)
                        .addInitParam(MessageServlet.MESSAGE, "done").addMapping("/target"));
    }

    @Test
    public void testRepeatedCompleteDuringCompletionListener() throws Exception {
        assertCompleteReturns("initial");
    }

    @Test
    public void testRepeatedCompleteAfterDispatch() throws Exception {
        assertCompleteReturns("dispatch");
    }

    @Test
    public void testRepeatedCompleteOnIoThread() throws Exception {
        assertCompleteReturns("io");
    }

    @Test
    public void testRepeatedCompleteOnAsyncThread() throws Exception {
        assertCompleteReturns("worker");
    }

    private void assertCompleteReturns(String mode) throws Exception {
        try (TestHttpClient client = new TestHttpClient()) {
            HttpResponse response = client.execute(new HttpGet(DefaultServer.getDefaultServerURL()
                    + "/servletContext/complete?mode=" + mode));
            Assert.assertEquals(200, response.getStatusLine().getStatusCode());
            HttpClientUtils.readResponse(response);

            CompletionResult result = RESULTS.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull("Completion listener was not invoked", result);
            result.writerFinished.get(10, TimeUnit.SECONDS);
            Assert.assertTrue("Completion listener could not acquire the writer's lock", result.lockAcquired);
            Assert.assertEquals(1, result.completionCount.get());
        }
    }

    private static final class CompletionResult {
        private final ReentrantLock responseLock = new ReentrantLock();
        private final CountDownLatch writerReady = new CountDownLatch(1);
        private final CountDownLatch completionStarted = new CountDownLatch(1);
        private final CompletableFuture<Void> writerFinished = new CompletableFuture<>();
        private final AtomicInteger completionCount = new AtomicInteger();
        private boolean lockAcquired;
    }

    public static class CompleteServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            AsyncContext context = request.startAsync();
            CompletionResult result = new CompletionResult();
            context.addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) throws IOException {
                    result.completionCount.incrementAndGet();
                    result.completionStarted.countDown();
                    try {
                        result.lockAcquired = result.responseLock.tryLock(5, TimeUnit.SECONDS);
                        if (result.lockAcquired) {
                            result.responseLock.unlock();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    } finally {
                        RESULTS.add(result);
                    }
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                }

                @Override
                public void onError(AsyncEvent event) {
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                }
            });

            Thread writer = new Thread(() -> {
                result.responseLock.lock();
                try {
                    result.writerReady.countDown();
                    if (!result.completionStarted.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Completion listener did not start");
                    }
                    context.complete();
                    result.writerFinished.complete(null);
                } catch (Throwable e) {
                    result.writerFinished.completeExceptionally(e);
                } finally {
                    result.responseLock.unlock();
                }
            }, "async-complete-writer");
            writer.setDaemon(true);
            writer.start();

            try {
                if (!result.writerReady.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Writer did not acquire its lock");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            String mode = request.getParameter("mode");
            if ("dispatch".equals(mode)) {
                context.dispatch("/target");
                context.complete();
            } else if ("io".equals(mode)) {
                ((AsyncContextImpl) context).addAsyncTask(() ->
                        ((HttpServletRequestImpl) request).getExchange().getIoThread().execute(context::complete));
            } else if ("worker".equals(mode)) {
                context.start(context::complete);
            } else {
                context.complete();
            }
        }
    }
}
