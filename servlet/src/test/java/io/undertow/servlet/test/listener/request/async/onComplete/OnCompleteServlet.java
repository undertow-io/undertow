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

package io.undertow.servlet.test.listener.request.async.onComplete;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class OnCompleteServlet extends HttpServlet {

    public static final BlockingQueue<String> QUEUE = new LinkedBlockingDeque<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final AsyncContext ctx = req.startAsync();
        ctx.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) throws IOException {
                QUEUE.add("onComplete");
            }

            @Override
            public void onTimeout(AsyncEvent event) throws IOException {
                QUEUE.add("onTimeout");
            }

            @Override
            public void onError(AsyncEvent event) throws IOException {
                QUEUE.add("onError");

            }

            @Override
            public void onStartAsync(AsyncEvent event) throws IOException {
                QUEUE.add("onStartAsync");
            }
        });
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                ctx.dispatch("/message");
            }
        });
        thread.start();
    }
}
