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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class AsyncInputStreamServlet extends HttpServlet {


    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final int preamble = Math.max(0, req.getIntHeader("preamble"));
        final boolean offIoThread = req.getHeader("offIoThread") != null;
        final AsyncContext context = req.startAsync();
        context.setTimeout(60000);

        final ServletOutputStream outputStream = resp.getOutputStream();
        ServletInputStream inputStream = req.getInputStream();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int i = 0; i < preamble; i++) {
            int value = inputStream.read();
            assert value >= 0 : "Stream is finished";
            data.write(value);
        }
        final MyListener listener = new MyListener(outputStream, inputStream, data, context, offIoThread);
        inputStream.setReadListener(listener);
        if(!offIoThread) {
            outputStream.setWriteListener(listener);
        }

    }

    private class MyListener implements WriteListener, ReadListener {
        private final ServletOutputStream outputStream;
        private final ServletInputStream inputStream;
        private final ByteArrayOutputStream dataToWrite;
        private final AsyncContext context;
        private final boolean offIoThread;

        boolean done = false;

        int written = 0;

        MyListener(
                final ServletOutputStream outputStream,
                final ServletInputStream inputStream,
                ByteArrayOutputStream dataToWrite, final AsyncContext context,
                final boolean offIoThread) {
            this.outputStream = outputStream;
            this.inputStream = inputStream;
            this.dataToWrite = dataToWrite;
            this.context = context;
            this.offIoThread = offIoThread;
        }

        @Override
        public void onWritePossible() throws IOException {
            //we don't use async writes for the off IO thread case
            //as we can't make it thread safe
            if (offIoThread || outputStream.isReady()) {
                dataToWrite.writeTo(outputStream);
                written += dataToWrite.size();
                dataToWrite.reset();
                if (done) {
                    context.complete();
                }
            }
        }

        @Override
        public void onDataAvailable() throws IOException {
            if (offIoThread) {
                context.start(new Runnable() {
                    @Override
                    public void run() {
                        doOnDataAvailable();
                    }
                });
            } else {
                doOnDataAvailable();
            }
        }

        private void doOnDataAvailable() {
            int read;
            try {
                while (inputStream.isReady()) {
                    read = inputStream.read();
                    if (read == 0) {
                        System.out.println("onDataAvailable> read 0x00");
                    }
                    if (read != -1) {
                        dataToWrite.write(read);
                    } else {
                        onWritePossible();
                    }
                }
            } catch (IOException e) {
                context.complete();
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized void onAllDataRead() throws IOException {
            done = true;
            if(offIoThread) {
                context.start(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            onWritePossible();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else {

                onWritePossible();
            }
        }

        @Override
        public synchronized void onError(final Throwable t) {
            t.printStackTrace();
        }
    }
}
