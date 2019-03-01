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
package io.undertow.websockets.core.protocol.server;

import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.protocol.http.HttpOpenListener;
import io.undertow.util.Transfer;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * This class is intended for use with testing against the Python
 * <a href="http://www.tavendo.de/autobahn/testsuite.html">AutoBahn test suite</a>.
 * <p>
 * Autobahn installation documentation can be found <a href="http://autobahn.ws/testsuite/installation">here</a>.
 * <p>
 * <h3>How to run the tests on Linux/OSX.</h3>
 * <p>
 * <p>01. Install AutoBahn: <tt>sudo easy_install autobahntestsuite</tt>.  Test using <tt>wstest --help</tt>.
 * <p>
 * <p>02. Create a directory for test configuration and results: <tt>mkdir ~/autobahn</tt> <tt>cd ~/autobahn</tt>.
 * <p>
 * <p>03. Create <tt>fuzzing_client_spec.json</tt> in the above directory
 * {@code
 * {
 * "options": {"failByDrop": false},
 * "outdir": "./reports/servers",
 * <p>
 * "servers": [
 * {"agent": "Netty4",
 * "url": "ws://localhost:9000",
 * "options": {"version": 18}}
 * ],
 * <p>
 * "cases": ["*"],
 * "exclude-cases": [],
 * "exclude-agent-cases": {}
 * }
 * }
 * <p>
 * <p>04. Run the <tt>AutobahnServer</tt> located in this package. If you are in Eclipse IDE, right click on
 * <tt>AutobahnServer.java</tt> and select Run As > Java Application.
 * <p>
 * <p>05. Run the Autobahn test <tt>wstest -m fuzzingclient -s fuzzing_client_spec.json</tt>.
 * <p>
 * <p>06. See the results in <tt>./reports/servers/index.html</tt>
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class AutobahnWebSocketServer {
    private HttpOpenListener openListener;
    private XnioWorker worker;
    private AcceptingChannel<StreamConnection> server;
    private Xnio xnio;
    private final int port;

    public static WebSocketChannel current;

    public AutobahnWebSocketServer(int port) {
        this.port = port;
    }

    private static final ChannelExceptionHandler<StreamSinkFrameChannel> W_H = new ChannelExceptionHandler<StreamSinkFrameChannel>() {
        @Override
        public void handleException(StreamSinkFrameChannel channel, IOException exception) {
            exception.printStackTrace();
        }
    };

    private static final ChannelExceptionHandler<StreamSourceFrameChannel> R_H = new ChannelExceptionHandler<StreamSourceFrameChannel>() {
        @Override
        public void handleException(StreamSourceFrameChannel channel, IOException exception) {
            exception.printStackTrace();
        }
    };

    public void run() {
        xnio = Xnio.getInstance();
        try {
            worker = xnio.createWorker(OptionMap.builder()
                    .set(Options.WORKER_WRITE_THREADS, 4)
                    .set(Options.WORKER_READ_THREADS, 4)
                    .set(Options.CONNECTION_HIGH_WATER, 1000000)
                    .set(Options.CONNECTION_LOW_WATER, 1000000)
                    .set(Options.WORKER_TASK_CORE_THREADS, 10)
                    .set(Options.WORKER_TASK_MAX_THREADS, 12)
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.CORK, true)
                    .getMap());

            OptionMap serverOptions = OptionMap.builder()
                    .set(Options.WORKER_ACCEPT_THREADS, 4)
                    .set(Options.TCP_NODELAY, true)
                    .set(Options.REUSE_ADDRESSES, true)
                    .getMap();
            openListener = new HttpOpenListener(new DefaultByteBufferPool(false, 8192));
            ChannelListener acceptListener = ChannelListeners.openListenerAdapter(openListener);
            server = worker.createStreamConnectionServer(new InetSocketAddress(port), acceptListener, serverOptions);


            setRootHandler(getRootHandler());
            server.resumeAccepts();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static WebSocketProtocolHandshakeHandler getRootHandler() {
        return new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {
            @Override
            public void onConnect(final WebSocketHttpExchange exchange, final WebSocketChannel channel) {
                current = channel;
                channel.getReceiveSetter().set(new Receiver());
                channel.resumeReceives();
            }
        });
    }

    private static final class Receiver implements ChannelListener<WebSocketChannel> {

        @Override
        public void handleEvent(final WebSocketChannel channel) {
            try {
                final StreamSourceFrameChannel ws = channel.receive();
                if (ws != null) {
                    StreamSinkFrameChannel target;
                    if (ws.getType() == WebSocketFrameType.PING ||
                            ws.getType() == WebSocketFrameType.CLOSE) {
                        target = channel.send(ws.getType() == WebSocketFrameType.PING ? WebSocketFrameType.PONG : WebSocketFrameType.CLOSE);
                    } else if (ws.getType() == WebSocketFrameType.PONG) {
                        ws.getReadSetter().set(ChannelListeners.drainListener(Long.MAX_VALUE, null, null));
                        ws.wakeupReads();
                        return;
                    } else {
                        target = channel.send(ws.getType());
                    }
                    Transfer.initiateTransfer(ws, target, null, ChannelListeners.writeShutdownChannelListener(new ChannelListener<StreamSinkFrameChannel>() {
                        @Override
                        public void handleEvent(StreamSinkFrameChannel c) {
                            channel.resumeReceives();
                        }
                    }, W_H), R_H, W_H, channel.getBufferPool());

                }
            } catch (IOException e) {
                e.printStackTrace();
                //IoUtils.safeClose(channel);
            }
        }
    }

    /**
     * Sets the root handler for the default web server
     *
     * @param rootHandler The handler to use
     */
    private void setRootHandler(HttpHandler rootHandler) {
        openListener.setRootHandler(rootHandler);
    }

    public static void main(String[] args) {
        new AutobahnWebSocketServer(7777).run();
    }


}
