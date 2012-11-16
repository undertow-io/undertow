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
import org.xnio.Buffers;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
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
            openListener = new HttpOpenListener(new ByteBufferSlicePool(BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR, 8192, 8192 * 8192));
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
                long size = ws.getPayloadSize();
                if (size == -1) {
                    // Fix this
                    size = 128 * 1024;
                }

                final ByteBuffer buffer;
                if (size == 0) {
                    buffer = Buffers.EMPTY_BYTE_BUFFER;
                } else {
                    buffer = ByteBuffer.allocate((int) size);
                }
                for (;;) {
                    int r = ws.read(buffer);
                    if (r == 0) {
                        ws.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                            @Override
                            public void handleEvent(StreamSourceChannel ch) {
                                try {
                                    for (;;) {
                                        int r = ch.read(buffer);
                                        if (r == 0) {
                                            return;
                                        } else if (r == -1) {
                                            break;
                                        }
                                    }
                                    write(channel, (StreamSourceFrameChannel) ch, buffer);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    IoUtils.safeClose(ch);
                                    IoUtils.safeClose(channel);
                                }

                            }
                        });
                        return;
                    } else if (r == -1) {
                        System.out.println("YO");
                        break;
                    }
                }
                write(channel, ws, buffer);
            } catch (IOException e) {
                e.printStackTrace();
                IoUtils.safeClose(channel);
            }
        }
    }

    private void write(final WebSocketChannel channel, final StreamSourceFrameChannel source, final ByteBuffer buffer) throws IOException {
        buffer.flip();
        StreamSinkFrameChannel sink = channel.send(source.getType(), buffer.remaining());
        sink.setFinalFragment(source.isFinalFragment());
        sink.setRsv(source.getRsv());
        source.close();

        while(buffer.hasRemaining()) {
            if (sink.write(buffer) == 0) {
                sink.getWriteSetter().set(new ChannelListener<StreamSinkFrameChannel>() {
                    @Override
                    public void handleEvent(StreamSinkFrameChannel ch) {
                         try {
                             while(buffer.hasRemaining()) {
                                 if (ch.write(buffer) == 0) {
                                     return;
                                 }
                             }
                             ch.shutdownWrites();
                             if (!ch.flush()) {
                                 ch.getWriteSetter().set(ChannelListeners.flushingChannelListener(new ChannelListener<StreamSinkChannel>() {
                                     @Override
                                     public void handleEvent(final StreamSinkChannel ch) {
                                         ch.getWriteSetter().set(null);

                                         IoUtils.safeClose(ch, source);
                                         if (source.getType() == WebSocketFrameType.CLOSE)  {
                                             IoUtils.safeClose(channel);
                                         }
                                     }
                                 }, ChannelListeners.closingChannelExceptionHandler()));
                                 ch.resumeWrites();
                             } else {
                                 ch.getWriteSetter().set(null);
                                 IoUtils.safeClose(ch, source);

                                 if (source.getType() == WebSocketFrameType.CLOSE)  {
                                     IoUtils.safeClose(channel);
                                 }

                             }
                         } catch (IOException e) {
                             e.printStackTrace();
                             ch.getWriteSetter().set(null);
                             IoUtils.safeClose(ch, channel, source);

                         }
                    }
                });
                sink.resumeWrites();
                return;
            }
        }
        sink.shutdownWrites();
        if (!sink.flush()) {
            sink.getWriteSetter().set(ChannelListeners.flushingChannelListener(new ChannelListener<StreamSinkChannel>() {
                @Override
                public void handleEvent(final StreamSinkChannel ch) {
                    ch.getWriteSetter().set(null);
                    IoUtils.safeClose(ch, source);
                    if (source.getType() == WebSocketFrameType.CLOSE)  {
                        IoUtils.safeClose(channel);
                    }
                }
            }, ChannelListeners.closingChannelExceptionHandler()));
            sink.resumeWrites();
        } else {
            IoUtils.safeClose(sink, source);
            if (source.getType() == WebSocketFrameType.CLOSE)  {
                IoUtils.safeClose(channel);
            }
            System.out.println("FFFF");
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
