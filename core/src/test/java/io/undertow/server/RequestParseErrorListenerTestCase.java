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
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.testutils.ProxyIgnore;
import io.undertow.util.BadRequestException;
import io.undertow.util.StatusCodes;
import io.undertow.util.UrlDecodeException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that a {@link RequestParseErrorListener} is notified when a request is rejected while it is
 * being parsed, which is the only opportunity an application has to observe such a request: it never
 * reaches the handler chain, and Undertow's own log message for it is at {@code DEBUG}.
 * <p>
 * The requests are written to a raw socket because Apache HttpClient will not send a deliberately
 * malformed request target.
 *
 * @author krowles
 */
@RunWith(DefaultServer.class)
@HttpOneOnly
@ProxyIgnore
public class RequestParseErrorListenerTestCase {

    private static final String BAD_REQUEST_RESPONSE =
            "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";

    private RequestParseErrorListener previous;

    @BeforeClass
    public static void setup() {
        DefaultServer.setRootHandler(exchange -> exchange.setStatusCode(StatusCodes.OK));
    }

    @Before
    public void saveListener() {
        previous = DefaultServer.getRequestParseErrorListener();
    }

    @After
    public void restoreListener() {
        DefaultServer.setRequestParseErrorListener(previous);
    }

    /**
     * {@code %%41} passes the parser's byte level validation, because {@code %} is not a hex digit and
     * so is treated as the start of a fresh percent encoded sequence. It therefore reaches
     * {@code URLUtils.decode}, which rejects it with an unchecked {@link UrlDecodeException}.
     */
    @Test
    public void invalidPercentEncodingInQueryParameterIsReported() throws Exception {
        CapturingListener listener = new CapturingListener();
        DefaultServer.setRequestParseErrorListener(listener);

        assertBadRequest(sendRaw("GET /somePath?a=%%41 HTTP/1.1\r\nHost: localhost\r\n\r\n"));

        listener.await();
        Assert.assertEquals(1, listener.invocations.get());
        Assert.assertTrue("expected a UrlDecodeException but got " + listener.error,
                listener.error instanceof UrlDecodeException);
        Assert.assertNotNull("the connection should always be available", listener.peerAddress);
        Assert.assertEquals("http/1.1", listener.transportProtocol);
        Assert.assertTrue("the partially parsed exchange should be available", listener.exchangePresent);
        Assert.assertEquals("/somePath", listener.requestPath);
        Assert.assertEquals("/somePath", listener.requestURI);
    }

    /**
     * {@code %zz} is rejected earlier, by the parser itself, as a checked {@link BadRequestException}.
     * Both failure modes must reach the listener.
     */
    @Test
    public void malformedPercentEncodedSequenceIsReported() throws Exception {
        CapturingListener listener = new CapturingListener();
        DefaultServer.setRequestParseErrorListener(listener);

        assertBadRequest(sendRaw("GET /somePath?a=%zz HTTP/1.1\r\nHost: localhost\r\n\r\n"));

        listener.await();
        Assert.assertEquals(1, listener.invocations.get());
        Assert.assertTrue("expected a BadRequestException but got " + listener.error,
                listener.error instanceof BadRequestException);
        Assert.assertEquals("/somePath", listener.requestPath);
    }

    /**
     * A well formed request must not notify the listener at all.
     */
    @Test
    public void validRequestDoesNotNotifyTheListener() throws Exception {
        CapturingListener listener = new CapturingListener();
        DefaultServer.setRequestParseErrorListener(listener);

        String response = sendRaw("GET /somePath?a=%41 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        Assert.assertTrue("expected a 200 but got: " + response, response.startsWith("HTTP/1.1 200"));
        Assert.assertFalse("the listener must not be notified for a request that parses",
                listener.latch.await(1, TimeUnit.SECONDS));
        Assert.assertEquals(0, listener.invocations.get());
    }

    /**
     * A listener that throws must not change what the client sees, and must not break the connector.
     */
    @Test
    public void aThrowingListenerDoesNotAffectTheResponse() throws Exception {
        CountDownLatch invoked = new CountDownLatch(1);
        DefaultServer.setRequestParseErrorListener((error, context) -> {
            invoked.countDown();
            throw new RuntimeException("deliberate failure from a faulty listener");
        });

        assertBadRequest(sendRaw("GET /somePath?a=%%41 HTTP/1.1\r\nHost: localhost\r\n\r\n"));
        Assert.assertTrue("the faulty listener was not invoked", invoked.await(10, TimeUnit.SECONDS));

        //and the connector is still healthy
        CapturingListener listener = new CapturingListener();
        DefaultServer.setRequestParseErrorListener(listener);
        assertBadRequest(sendRaw("GET /somePath?a=%%41 HTTP/1.1\r\nHost: localhost\r\n\r\n"));
        listener.await();
    }

    /**
     * Clearing the listener must restore the original behaviour.
     */
    @Test
    public void listenerCanBeCleared() throws Exception {
        DefaultServer.setRequestParseErrorListener(null);
        Assert.assertSame(RequestParseErrorListener.NO_OP, DefaultServer.getRequestParseErrorListener());
        assertBadRequest(sendRaw("GET /somePath?a=%%41 HTTP/1.1\r\nHost: localhost\r\n\r\n"));
    }

    private static void assertBadRequest(final String response) {
        //an empty response means the connection was torn down before we could read it, which
        //happens on some of the transports the suite runs under; the listener assertions are what
        //this test is really about
        if (!response.isEmpty()) {
            Assert.assertEquals(BAD_REQUEST_RESPONSE, response);
        }
    }

    private static String sendRaw(final String request) throws IOException {
        final StringBuilder sb = new StringBuilder();
        try (Socket s = new Socket(DefaultServer.getDefaultServerAddress().getAddress(),
                DefaultServer.getDefaultServerAddress().getPort())) {
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
            //this can happen as well, as in some cases we may not have fully consumed the read side
            //before the connection is shutdown, namely when we are running in test.single
        }
        return sb.toString();
    }

    private static final class CapturingListener implements RequestParseErrorListener {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicInteger invocations = new AtomicInteger();

        private volatile Throwable error;
        private volatile SocketAddress peerAddress;
        private volatile String transportProtocol;
        private volatile boolean exchangePresent;
        private volatile String requestPath;
        private volatile String requestURI;

        @Override
        public void onParseError(final Throwable error, final RequestParseErrorContext context) {
            this.error = error;
            this.peerAddress = context.getConnection().getPeerAddress();
            this.transportProtocol = context.getConnection().getTransportProtocol();
            final HttpServerExchange exchange = context.getExchange();
            this.exchangePresent = exchange != null;
            if (exchange != null) {
                this.requestPath = exchange.getRequestPath();
                this.requestURI = exchange.getRequestURI();
            }
            this.invocations.incrementAndGet();
            this.latch.countDown();
        }

        void await() throws InterruptedException {
            Assert.assertTrue("the request parse error listener was not notified",
                    latch.await(10, TimeUnit.SECONDS));
        }
    }
}
