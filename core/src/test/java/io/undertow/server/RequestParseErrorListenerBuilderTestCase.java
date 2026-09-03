/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.undertow.Undertow;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.StatusCodes;
import io.undertow.util.UrlDecodeException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.xnio.Options;

/**
 * Tests that a {@link RequestParseErrorListener} can be configured through
 * {@link Undertow.Builder#setRequestParseErrorListener(RequestParseErrorListener)} and is then used by
 * the listeners that {@link Undertow#start()} creates.
 * <p>
 * This runs its own server rather than using {@code DefaultServer}, because {@code DefaultServer}
 * assembles its open listeners by hand and never goes through {@code Undertow.Builder}.
 *
 * @author krowles
 */
public class RequestParseErrorListenerBuilderTestCase {

    private Undertow undertow;

    @After
    public void stopServer() {
        if (undertow != null) {
            undertow.stop();
            undertow = null;
        }
    }

    @Test
    public void builderConfiguredListenerIsNotifiedOfParseErrors() throws Exception {
        final CountDownLatch invoked = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<String> requestPath = new AtomicReference<>();
        final AtomicReference<String> transportProtocol = new AtomicReference<>();

        undertow = Undertow.builder()
                .addListener(new Undertow.ListenerBuilder()
                        .setType(Undertow.ListenerType.HTTP)
                        .setHost(DefaultServer.getHostAddress())
                        .setPort(0))
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setRequestParseErrorListener((e, context) -> {
                    error.set(e);
                    transportProtocol.set(context.getConnection().getTransportProtocol());
                    if (context.getExchange() != null) {
                        requestPath.set(context.getExchange().getRequestPath());
                    }
                    invoked.countDown();
                })
                .setHandler(exchange -> exchange.setStatusCode(StatusCodes.OK))
                .build();
        undertow.start();

        sendRaw(boundAddress(), "GET /somePath?a=%%41 HTTP/1.1\r\nHost: localhost\r\n\r\n");

        Assert.assertTrue("the request parse error listener was not notified", invoked.await(10, TimeUnit.SECONDS));
        Assert.assertTrue("expected a UrlDecodeException but got " + error.get(),
                error.get() instanceof UrlDecodeException);
        Assert.assertEquals("http/1.1", transportProtocol.get());
        Assert.assertEquals("/somePath", requestPath.get());
    }

    /**
     * Not configuring a listener must leave the server behaving exactly as before.
     */
    @Test
    public void aServerWithNoListenerStillRejectsTheRequest() throws Exception {
        undertow = Undertow.builder()
                .addListener(new Undertow.ListenerBuilder()
                        .setType(Undertow.ListenerType.HTTP)
                        .setHost(DefaultServer.getHostAddress())
                        .setPort(0))
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setHandler(exchange -> exchange.setStatusCode(StatusCodes.OK))
                .build();
        undertow.start();

        String response = sendRaw(boundAddress(), "GET /somePath?a=%%41 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        if (!response.isEmpty()) {
            Assert.assertTrue("expected a 400 but got: " + response, response.startsWith("HTTP/1.1 400"));
        }
    }

    private InetSocketAddress boundAddress() {
        return (InetSocketAddress) undertow.getListenerInfo().get(0).getAddress();
    }

    private static String sendRaw(final InetSocketAddress address, final String request) throws IOException {
        final StringBuilder sb = new StringBuilder();
        try (Socket s = new Socket(address.getAddress(), address.getPort())) {
            s.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            s.getOutputStream().flush();
            final byte[] buf = new byte[256];
            while (true) {
                int r = s.getInputStream().read(buf);
                if (r <= 0) {
                    break;
                }
                sb.append(new String(buf, 0, r, java.nio.charset.StandardCharsets.US_ASCII));
            }
        } catch (IOException expected) {
            //the connection may be torn down before the response is fully consumed
        }
        return sb.toString();
    }
}
