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

import jakarta.servlet.ServletException;
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
public class UpgradeServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        req.upgrade(Handler.class);
    }

    public static class Handler implements HttpUpgradeHandler {

        @Override
        public void init(final WebConnection wc) {
            try {
                String message = "";
                do {
                    //an incredibly poxy implementation of an echo server, that uses /r/n/r/n to delineate messages
                    final StringBuilder builder = new StringBuilder();
                    byte[] data = new byte[100];
                    int read;
                    while (!builder.toString().endsWith("\r\n\r\n") && (read = wc.getInputStream().read(data)) != -1) {
                        builder.append(new String(data, 0, read));
                    }
                    wc.getOutputStream().print(builder.toString());
                    wc.getOutputStream().flush();
                    message = builder.toString();
                } while (!"exit\r\n\r\n".equals(message));
            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);

            } finally {

                try {
                    wc.close();
                } catch (Exception e) {
                    UndertowLogger.REQUEST_IO_LOGGER.ioException(new IOException(e));
                }
            }
        }

        @Override
        public void destroy() {

        }
    }

}
