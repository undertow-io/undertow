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

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Stuart Douglas
 */
public class AsyncInputStreamServlet extends HttpServlet {


    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {

        final AsyncContext context = req.startAsync();

        final ServletOutputStream outputStream = resp.getOutputStream();
        ServletInputStream inputStream = req.getInputStream();
        final MyListener listener = new MyListener(outputStream, inputStream, context);
        inputStream.setReadListener(listener);
        outputStream.setWriteListener(listener);

    }

    private class MyListener implements WriteListener, ReadListener {
        private final ServletOutputStream outputStream;
        private final ServletInputStream inputStream;
        private final ByteArrayOutputStream dataToWrite = new ByteArrayOutputStream();
        private final AsyncContext context;

        boolean done = false;

        int written = 0;

        MyListener(final ServletOutputStream outputStream, final ServletInputStream inputStream, final AsyncContext context) {
            this.outputStream = outputStream;
            this.inputStream = inputStream;
            this.context = context;
        }

        @Override
        public void onWritePossible() throws IOException {
            if (outputStream.isReady()) {
                outputStream.write(dataToWrite.toByteArray());
                written += dataToWrite.toByteArray().length;
                dataToWrite.reset();
                if (done) {
                    context.complete();
                }
            }
        }

        @Override
        public void onDataAvailable() throws IOException {
            int read;
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
