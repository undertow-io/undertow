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
public class EagerAsyncInputStreamServlet extends HttpServlet {


    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        req.startAsync();
        MyListener listener = new MyListener(resp.getOutputStream(), req.getInputStream(), new ByteArrayOutputStream(), req.getAsyncContext());
        resp.getOutputStream().setWriteListener(listener);
        req.getInputStream().setReadListener(listener);
        req.getInputStream().isReady();
    }

    private class MyListener implements WriteListener, ReadListener {
        private final ServletOutputStream outputStream;
        private final ServletInputStream inputStream;
        private final ByteArrayOutputStream dataToWrite;
        private final AsyncContext context;

        boolean done = false;

        int written = 0;

        MyListener(
                final ServletOutputStream outputStream,
                final ServletInputStream inputStream,
                ByteArrayOutputStream dataToWrite, final AsyncContext context) {
            this.outputStream = outputStream;
            this.inputStream = inputStream;
            this.dataToWrite = dataToWrite;
            this.context = context;
        }

        @Override
        public void onWritePossible() throws IOException {
            //we don't use async writes for the off IO thread case
            //as we can't make it thread safe
            if (outputStream.isReady()) {
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
            doOnDataAvailable();
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
            onWritePossible();
        }

        @Override
        public synchronized void onError(final Throwable t) {
            t.printStackTrace();
        }
    }
}
