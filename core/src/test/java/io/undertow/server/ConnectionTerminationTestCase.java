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

package io.undertow.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoUtils;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.util.FileUtils;

/**
 * Tests abnormal connection termination
 *
 * @author Stuart Douglas
 */
@RunWith(DefaultServer.class)
@ProxyIgnore
@HttpOneOnly
public class ConnectionTerminationTestCase {

    private volatile boolean completionListenerCalled = false;
    private final CountDownLatch completionListenerCalledLatch = new CountDownLatch(1);

    @Test
    public void testAbnormalRequestTermination() throws IOException, InterruptedException {
        DefaultServer.setRootHandler(new HttpHandler() {
            @Override
            public void handleRequest(final HttpServerExchange exchange) throws Exception {
                if (exchange.isInIoThread()) {
                    exchange.dispatch(this);
                    return;
                }
                exchange.startBlocking();
                exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                    @Override
                    public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                        completionListenerCalled = true;
                        completionListenerCalledLatch.countDown();
                        nextListener.proceed();
                    }
                });
                final InputStream request = exchange.getInputStream();
                String data = FileUtils.readFile(request);
                exchange.getOutputStream().write(data.getBytes("UTF-8"));
            }
        });

        Socket socket = new Socket();
        socket.connect(DefaultServer.getDefaultServerAddress());
        try {

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; ++i) {
                sb.append("hello world\r\n");
            }
            //send a large request that is too small, then kill the socket
            String request = "POST / HTTP/1.1\r\nHost:localhost\r\nContent-Length:" + sb.length() + 100 + "\r\n\r\n" + sb.toString();
            OutputStream outputStream = socket.getOutputStream();

            outputStream.write(request.getBytes("US-ASCII"));
            socket.getInputStream().close();
            outputStream.close();

            //make sure the completion listener is still called
            //this is important, as this can be used for resource cleanup
            completionListenerCalledLatch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue(completionListenerCalled);

        } finally {
            IoUtils.safeClose(socket);
        }
    }
}
