/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.listener.request.async.onError;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author baranowb
 */
public class AsyncServlet5 extends HttpServlet {
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AsyncContext ctx =  req.startAsync();
        resp.setContentType("text/event-stream");
        resp.setHeader("Cache-Control", "no-cache");
        ctx.setTimeout(10_000L);
        ctx.addListener(new AsyncEventListener() {

            @Override
            public void onError(AsyncEvent event) throws IOException {
                super.onError(event);
                latch.countDown();
            }
        });
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final ScheduledFuture<?> scheduledFuture = executorService.scheduleAtFixedRate(new Job(ctx), 0, 1, SECONDS);
                try {
                    latch.await(11, SECONDS);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                scheduledFuture.cancel(true);
                ctx.complete();
            }
        });
        t.start();
    }
    private class Job implements Runnable {
        private final AsyncContext asyncContext;

        Job(AsyncContext asyncContext) {
            this.asyncContext = asyncContext;
        }

        @Override
        public void run() {
            try {
                final ServletResponse response = asyncContext.getResponse();
                response.getOutputStream().print(getMessage());
                response.flushBuffer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String getMessage() {
            return new StringBuilder("data:")
                    .append(LocalDateTime.now())
                    .append("\n\n")
                    .toString();
        }
    }
}
