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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class AsyncOutputStreamServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final boolean flush = req.getParameter("flush") != null;
        final boolean close = req.getParameter("close") != null;
        final boolean preable = req.getParameter("preamble") != null;
        final boolean offIoThread = req.getParameter("offIoThread") != null;
        final int reps = Integer.parseInt(req.getParameter("reps"));

        final AtomicInteger count = new AtomicInteger();

        final AsyncContext context = req.startAsync();
        final ServletOutputStream outputStream = resp.getOutputStream();
        if(preable) {
            for(int i = 0; i < reps; ++i) {
                outputStream.write(ServletOutputStreamTestCase.message.getBytes());
            }
        }
        WriteListener listener = new WriteListener() {
            @Override
            public synchronized void onWritePossible() throws IOException {
                final AtomicBoolean writing = new AtomicBoolean();
                while (outputStream.isReady() && count.get() < reps && writing.compareAndSet(false, true)) {
                    count.incrementAndGet();
                    outputStream.write(ServletOutputStreamTestCase.message.getBytes());
                    writing.set(false);
                }
                if (count.get() == reps && writing.compareAndSet(false, true)) {
                    if (flush) {
                        outputStream.flush();
                    }
                    if (close) {
                        outputStream.close();
                    }
                    context.complete();
                }
            }

            @Override
            public void onError(final Throwable t) {

            }
        };
        outputStream.setWriteListener(offIoThread ? new WriteListener() {
            @Override
            public void onWritePossible() throws IOException {
                context.start(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            listener.onWritePossible();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }

            @Override
            public void onError(Throwable throwable) {

            }
        } : listener);
    }
}
