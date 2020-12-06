/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package io.undertow.server.handlers;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
public class HttpContinueConduitWrappingHandlerBufferLeakTestCase {
    static Socket persistentSocket;

    @BeforeClass
    public static void setup() {
        final BlockingHandler blockingHandler = new BlockingHandler();
        final HttpContinueReadHandler handler = new HttpContinueReadHandler(blockingHandler);
        DefaultServer.setRootHandler(handler);
        blockingHandler.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) {
                try {
                    if (exchange.getQueryParameters().containsKey("reject")) {
                        exchange.getRequestChannel();
                        exchange.setStatusCode(StatusCodes.EXPECTATION_FAILED);
                        exchange.getOutputStream().close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Before
    public void before() {
        Assume.assumeFalse(DefaultServer.isAjp());
    }

    @Test
    public void testHttpContinueRejectedBodySentAnywayNoBufferLeak() throws IOException {
        persistentSocket = new Socket(DefaultServer.getHostAddress(), DefaultServer.getHostPort());

        String message = "POST /path?reject=true HTTP/1.1\r\n" +
                "Expect: 100-continue\r\n" +
                "Content-Length: 16\r\n" +
                "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                "Host: localhost:7777\r\n" +
                "Connection: Keep-Alive\r\n\r\nMy HTTP Request!";
        persistentSocket.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
        persistentSocket.getOutputStream().flush();
        persistentSocket.getInputStream().read();
    }

    @Test
    public void testHttpContinueBodySentAnywayNoLeak() throws IOException {
        persistentSocket = new Socket(DefaultServer.getHostAddress(), DefaultServer.getHostPort());

        String message = "POST /path HTTP/1.1\r\n" +
                "Expect: 100-continue\r\n" +
                "Content-Length: 16\r\n" +
                "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                "Host: localhost:7777\r\n" +
                "Connection: Keep-Alive\r\n\r\nMy HTTP Request!";
        persistentSocket.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
        persistentSocket.getOutputStream().flush();
        persistentSocket.getInputStream().read();
    }

    @Test
    public void testEmptySSLHttpContinueNoLeak() throws IOException {
        DefaultServer.startSSLServer();

        try {
            final Socket sslSocket = DefaultServer.getClientSSLContext().getSocketFactory().createSocket(
                    new Socket(DefaultServer.getHostAddress("default"),
                            DefaultServer.getHostSSLPort("default")),
                    DefaultServer.getHostAddress("default"),
                    DefaultServer.getHostSSLPort("default"), true);

            String header = DefaultServer.isH2()?"POST /path HTTP/2.0":"POST /path HTTP/1.1";

            String message = header +
                    "Expect: 100-continue\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "Host: localhost:7778\r\n" +
                    "Connection: Keep-Alive\r\n\r\n";
            sslSocket.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
            sslSocket.getOutputStream().flush();
            sslSocket.getInputStream().read();
        } finally {
            DefaultServer.stopSSLServer();
        }
    }

}
