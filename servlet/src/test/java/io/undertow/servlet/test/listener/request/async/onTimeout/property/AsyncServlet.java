/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

package io.undertow.servlet.test.listener.request.async.onTimeout.property;

import java.io.IOException;

import io.undertow.servlet.spec.AsyncContextImpl;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author baranowb
 */
public class AsyncServlet extends HttpServlet {

    public static final String TEST_TIMEOUT="timeout";
    public static final String TIMEOUT_START_TSTAMP = "tstamp";
    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        long timeout = Long.parseLong((String)req.getParameter(TEST_TIMEOUT));
        System.setProperty(AsyncContextImpl.ASYNC_CONTEXT_TIMEOUT, timeout+"");
        req.setAttribute(TIMEOUT_START_TSTAMP, System.currentTimeMillis());
        AsyncContext ctx = req.startAsync();
        ctx.addListener(new SimpleAsyncListener());
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(timeout+3000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.setProperty(AsyncContextImpl.ASYNC_CONTEXT_TIMEOUT, "30000");
            }
        });
        t.start();
    }
}
