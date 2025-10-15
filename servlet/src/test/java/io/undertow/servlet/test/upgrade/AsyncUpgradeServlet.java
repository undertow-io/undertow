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

package io.undertow.servlet.test.upgrade;

import java.io.IOException;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.WebConnection;

import io.undertow.UndertowLogger;

/**
 * Simple upgrade servlet. Because the apache http client does not handle upgrades we keep faking http
 * after we upgrade
 *
 * @author Stuart Douglas
 */
public class AsyncUpgradeServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        req.upgrade(Handler.class);
    }

    public static class Handler implements HttpUpgradeHandler {

        @Override
        public void init(final WebConnection wc) {
            Listener listener = new Listener(wc);
            try {
                //we have to set the write listener before the read listener
                //otherwise the output stream could be written to before it is
                //in async mode
                wc.getOutputStream().setWriteListener(listener);
                wc.getInputStream().setReadListener(listener);
            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            }
        }

        @Override
        public void destroy() {

        }
    }


    private static class Listener implements WriteListener, ReadListener {

        private final WebConnection connection;
        StringBuilder builder = new StringBuilder();

        boolean reading = true;

        private Listener(final WebConnection connection) {
            this.connection = connection;
        }

        @Override
        public synchronized void onDataAvailable() throws IOException {
            byte[] data = new byte[100];
            while (connection.getInputStream().isReady()) {
                int read;
                if ((read = connection.getInputStream().read(data)) != -1) {
                    builder.append(new String(data, 0, read));
                }
                if (builder.toString().endsWith("\r\n\r\n")) {
                    reading = false;
                    onWritePossible();
                }
            }
        }

        @Override
        public void onAllDataRead() throws IOException {

        }

        @Override
        public synchronized void onWritePossible() throws IOException {
            if(builder.toString().equals("exit\r\n\r\n")) {
                try {
                    connection.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            if (reading) {
                return;
            }
            if (connection.getOutputStream().isReady()) {
                connection.getOutputStream().write(builder.toString().getBytes());
                builder = new StringBuilder();
                reading = true;
            }
        }

        @Override
        public void onError(final Throwable t) {

        }
    }
}
