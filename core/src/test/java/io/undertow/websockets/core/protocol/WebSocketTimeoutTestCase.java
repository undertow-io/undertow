/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.core.protocol;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.CharsetUtil;
import io.undertow.UndertowOptions;
import io.undertow.testutils.DefaultServer;
import io.undertow.testutils.HttpOneOnly;
import io.undertow.util.NetworkUtils;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.utils.FrameChecker;
import io.undertow.websockets.utils.WebSocketTestClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.FutureResult;
import org.xnio.OptionMap;
import org.xnio.Options;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(DefaultServer.class)
@HttpOneOnly
public class WebSocketTimeoutTestCase {

    protected static final int TESTABLE_TIMEOUT_VALUE = 2000;
    protected static final int NON_TESTABLE_TIMEOUT_VALUE = 30180;
    protected static final int DEFAULTS_IO_TIMEOUT_VALUE = 500;
    private static ScheduledExecutorService SCHEDULER = null;

    @DefaultServer.BeforeServerStarts
    public static void beforeTest() {
        DefaultServer.setServerOptions(OptionMap.builder()
                .set(Options.READ_TIMEOUT, DEFAULTS_IO_TIMEOUT_VALUE)
                .set(Options.WRITE_TIMEOUT, DEFAULTS_IO_TIMEOUT_VALUE)
                .set(UndertowOptions.WEB_SOCKETS_READ_TIMEOUT, TESTABLE_TIMEOUT_VALUE)
                .set(UndertowOptions.WEB_SOCKETS_WRITE_TIMEOUT, NON_TESTABLE_TIMEOUT_VALUE).getMap());
/*
        DefaultServer.setUndertowOptions(OptionMap.builder()
                .set(Options.READ_TIMEOUT, regularTimeouts)
                .set(Options.WRITE_TIMEOUT, regularTimeouts)
                .set(UndertowOptions.WEB_SOCKETS_READ_TIMEOUT, wsReadTimeout)
                .set(UndertowOptions.WEB_SOCKETS_WRITE_TIMEOUT, wsWriteTimeout).getMap());*/
        SCHEDULER = Executors.newScheduledThreadPool(2);
    }

    @DefaultServer.AfterServerStops
    public static void afterTest() {
        SCHEDULER.shutdown();
        DefaultServer.setServerOptions(OptionMap.EMPTY);
    }

    protected WebSocketVersion getVersion() {
        return WebSocketVersion.V13;
    }

    @Test
    public void testServerReadTimeout() throws Exception {
        final AtomicBoolean connected = new AtomicBoolean(false);
        DefaultServer.setRootHandler(new WebSocketProtocolHandshakeHandler(
                (WebSocketConnectionCallback) (exchange, channel) -> {
                    connected.set(true);
                    channel.getReceiveSetter().set(new AbstractReceiveListener() {
                        @Override
                        protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                            String string = message.getData();

                            if (string.equals("hello")) {
                                WebSockets.sendText("world", channel, null);
                            } else {
                                WebSockets.sendText(string, channel, null);
                            }
                        }
                    });
                    channel.resumeReceives();
                }));

        final FutureResult<?> latch = new FutureResult();
        WebSocketTestClient client = new WebSocketTestClient(getVersion(), new URI("ws://" + NetworkUtils.formatPossibleIpv6Address(DefaultServer.getHostAddress("default")) + ":" + DefaultServer.getHostPort("default") + "/"));
        client.connect();
        client.send(new TextWebSocketFrame(Unpooled.copiedBuffer("hello", CharsetUtil.US_ASCII)), new FrameChecker(TextWebSocketFrame.class, "world".getBytes(CharsetUtil.US_ASCII), latch));
        latch.getIoFuture().get();

        final long watchStart = System.currentTimeMillis();
        final long watchTimeout = System.currentTimeMillis() + TESTABLE_TIMEOUT_VALUE + 1000;
        final FutureResult<Long> timeoutLatch = new FutureResult<Long>();
        ReadTimeoutChannelGuard readTimeoutChannelGuard = new ReadTimeoutChannelGuard(client, timeoutLatch, watchTimeout);

        final ScheduledFuture sf = SCHEDULER.scheduleAtFixedRate(readTimeoutChannelGuard, 0, 50, TimeUnit.MILLISECONDS);
        readTimeoutChannelGuard.setTaskScheduledFuture(sf);

        final Long watchTimeEnd = timeoutLatch.getIoFuture().get();
        if(watchTimeEnd == -1) {
            Assert.fail("Timeout did not happen... in time. Were waiting '" + watchTimeout + "' ms, timeout should happen in '" + TESTABLE_TIMEOUT_VALUE + "' ms.");
        } else {
            long timeSpent = watchTimeEnd - watchStart;
            //let's be generous and give 150ms diff( there is "fuzz" coded for 50ms in undertow as well
            if(!(timeSpent <= TESTABLE_TIMEOUT_VALUE + 250)) {
                Assert.fail("Timeout did not happen... in time. Socket timeout out in '" + timeSpent + "' ms, supposed to happen in '" + TESTABLE_TIMEOUT_VALUE + "' ms.");
            }
        }
    }

    private static class ReadTimeoutChannelGuard implements Runnable {
        private final WebSocketTestClient channel;
        private final FutureResult<Long> resultHandler;
        private final long watchEnd;
        private ScheduledFuture<?> sf;

        ReadTimeoutChannelGuard(final WebSocketTestClient channel, final FutureResult<Long> resultHandler, final long watchEnd) {
            super();
            this.channel = channel;
            this.resultHandler = resultHandler;
            this.watchEnd = watchEnd;
        }

        public void setTaskScheduledFuture(ScheduledFuture sf2) {
            this.sf = sf2;
        }

        @Override
        public void run() {
            if (System.currentTimeMillis() > watchEnd) {
                sf.cancel(false);
                if(channelActive()) {
                    resultHandler.setResult(new Long(-1));
                } else {
                    resultHandler.setResult(System.currentTimeMillis());
                }
            } else {
                if(!channelActive()) {
                    sf.cancel(false);
                    resultHandler.setResult(System.currentTimeMillis());
                }
            }
        }

        private boolean channelActive() {
            return channel.isOpen();
        }

    }
 }
