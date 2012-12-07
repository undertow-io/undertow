/*
 * Copyright 2012 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets.protocol.server;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpOpenListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpTransferEncodingHandler;
import io.undertow.websockets.StreamSinkFrameChannel;
import io.undertow.websockets.StreamSourceFrameChannel;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketFrameType;
import io.undertow.websockets.handler.WebSocketConnectionCallback;
import io.undertow.websockets.handler.WebSocketProtocolHandshakeHandler;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * This class is intended for use with testing against the Python
 * <a href="http://www.tavendo.de/autobahn/testsuite.html">AutoBahn test suite</a>.
 *
 * Autobahn installation documentation can be found <a href="http://autobahn.ws/testsuite/installation">here</a>.
 *
 * <h3>How to run the tests on Linux/OSX.</h3>
 *
 * <p>01. Install AutoBahn: <tt>sudo easy_install autobahntestsuite</tt>.  Test using <tt>wstest --help</tt>.
 *
 * <p>02. Create a directory for test configuration and results: <tt>mkdir ~/autobahn</tt> <tt>cd ~/autobahn</tt>.
 *
 * <p>03. Create <tt>fuzzing_client_spec.json</tt> in the above directory
 * {@code
 * {
 *    "options": {"failByDrop": false},
 *    "outdir": "./reports/servers",
 *
 *    "servers": [
 *                 {"agent": "Netty4",
 *                  "url": "ws://localhost:9000",
 *                  "options": {"version": 18}}
 *               ],
 *
 *    "cases": ["*"],
 *    "exclude-cases": ["9.*"],
 *    "exclude-agent-cases": {}
 * }
 * }
 * Note that we disabled the <strong>9.*</strong> tests for now as these fail.
 *
 * <p>04. Run the <tt>AutobahnServer</tt> located in this package. If you are in Eclipse IDE, right click on
 * <tt>AutobahnServer.java</tt> and select Run As > Java Application.
 *
 * <p>05. Run the Autobahn test <tt>wstest -m fuzzingclient -s fuzzingclient.json</tt>.
 *
 * <p>06. See the results in <tt>./reports/servers/index.html</tt>
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class AutobahnWebSocketServer {
    private HttpOpenListener openListener;
    private XnioWorker worker;
    private AcceptingChannel<? extends ConnectedStreamChannel> server;
    private Xnio xnio;
    private final int port;

    public AutobahnWebSocketServer(int port) {
        this.port = port;
    }


    public void run() {
        xnio = Xnio.getInstance("nio");
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
            openListener = new HttpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 8192 * 8192), 8192);
            ChannelListener acceptListener = ChannelListeners.openListenerAdapter(openListener);
            server = worker.createStreamServer(new InetSocketAddress(port), acceptListener, serverOptions);



            setRootHandler(new WebSocketProtocolHandshakeHandler("/", new WebSocketConnectionCallback() {
                @Override
                public void onConnect(final HttpServerExchange exchange, final WebSocketChannel channel) {
                    channel.getReceiveSetter().set(new Receiver());
                    channel.resumeReceives();
                }
            }));
            server.resumeAccepts();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final class Receiver implements ChannelListener<WebSocketChannel> {

        @Override
        public void handleEvent(final WebSocketChannel channel) {
            try {
                final StreamSourceFrameChannel ws = channel.receive();
                if (ws == null) {
                    return;
                }

                final WebSocketFrameType type;
                switch (ws.getType()) {
                    case PONG:
                        // suspend receives until we have received the whole frame
                        channel.suspendReceives();
                        ws.getCloseSetter().set(new ChannelListener<StreamSourceChannel>() {
                            @Override
                            public void handleEvent(StreamSourceChannel o) {
                                // discard complete receive next frame
                                channel.resumeReceives();
                            }
                        });
                        // pong frames must be discarded
                        ws.discard();
                        return;
                    case PING:
                        // if a ping is send the autobahn testsuite expects a PONG when echo back
                        type = WebSocketFrameType.PONG;
                        break;
                    default:
                        type = ws.getType();
                        break;
                }

                long size = ws.getPayloadSize();

                final StreamSinkFrameChannel sink = channel.send(type, size);
                sink.setFinalFragment(ws.isFinalFragment());
                sink.setRsv(ws.getRsv());
                ChannelListeners.initiateTransfer(Long.MAX_VALUE, ws, sink, new ChannelListener<StreamSourceFrameChannel>() {
                            @Override
                            public void handleEvent(StreamSourceFrameChannel streamSourceFrameChannel) {
                                IoUtils.safeClose(streamSourceFrameChannel);
                            }
                        }, new ChannelListener<StreamSinkFrameChannel>() {
                            @Override
                            public void handleEvent(StreamSinkFrameChannel streamSinkFrameChannel) {
                                if(!streamSinkFrameChannel.isOpen()) {
                                    return;
                                }
                                try {
                                    streamSinkFrameChannel.shutdownWrites();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    IoUtils.safeClose(streamSinkFrameChannel, channel);
                                    return;
                                }
                                try {
                                    if (!streamSinkFrameChannel.flush()) {
                                        streamSinkFrameChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(
                                                new ChannelListener<StreamSinkFrameChannel>() {
                                                    @Override
                                                    public void handleEvent(StreamSinkFrameChannel streamSinkFrameChannel) {
                                                        streamSinkFrameChannel.getWriteSetter().set(null);
                                                        IoUtils.safeClose(streamSinkFrameChannel);
                                                        if (type == WebSocketFrameType.CLOSE) {
                                                            IoUtils.safeClose(channel);
                                                        }
                                                    }
                                                }, new ChannelExceptionHandler<StreamSinkFrameChannel>() {
                                                    @Override
                                                    public void handleException(StreamSinkFrameChannel o, IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                        ));
                                        streamSinkFrameChannel.resumeWrites();
                                    } else {
                                        if (type == WebSocketFrameType.CLOSE) {
                                            IoUtils.safeClose(channel);
                                        }
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }, new ChannelExceptionHandler<StreamSourceFrameChannel>() {
                            @Override
                            public void handleException(StreamSourceFrameChannel streamSourceFrameChannel, IOException e) {
                                e.printStackTrace();
                                IoUtils.safeClose(streamSourceFrameChannel, channel);
                            }
                        }, new ChannelExceptionHandler<StreamSinkFrameChannel>() {
                            @Override
                            public void handleException(StreamSinkFrameChannel streamSinkFrameChannel, IOException e) {
                                e.printStackTrace();

                                IoUtils.safeClose(streamSinkFrameChannel, channel);
                            }
                        }, channel.getBufferPool()
                );
            } catch (IOException e) {
                e.printStackTrace();
                IoUtils.safeClose(channel);
            }
        }
    }

    /**
     * Sets the root handler for the default web server
     *
     * @param rootHandler The handler to use
     */
    private void setRootHandler(HttpHandler rootHandler) {
        final HttpTransferEncodingHandler ph = new HttpTransferEncodingHandler();
        ph.setNext(rootHandler);
        openListener.setRootHandler(ph);
    }

    public static void main(String args[]) {
        new AutobahnWebSocketServer(7777).run();
    }
}
