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
import io.undertow.websockets.WebSocketLogger;
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
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;

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
                initiateTransfer(Long.MAX_VALUE, ws, sink, new ChannelListener<StreamSourceFrameChannel>() {
                            @Override
                            public void handleEvent(StreamSourceFrameChannel streamSourceFrameChannel) {
                                IoUtils.safeClose(streamSourceFrameChannel);
                            }
                        }, new ChannelListener<StreamSinkFrameChannel>() {
                            @Override
                            public void handleEvent(StreamSinkFrameChannel streamSinkFrameChannel) {
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
                                        streamSinkFrameChannel.getWriteSetter().set(null);
                                        IoUtils.safeClose(streamSinkFrameChannel);
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



    /**
     * Initiate a low-copy transfer between two stream channels.  The pool should be a direct buffer pool for best
     * performance.
     *
     * @param count the number of bytes to transfer, or {@link Long#MAX_VALUE} to transfer all remaining bytes
     * @param source the source channel
     * @param sink the target channel
     * @param sourceListener the source listener to set and call when the transfer is complete, or {@code null} to clear the listener at that time
     * @param sinkListener the target listener to set and call when the transfer is complete, or {@code null} to clear the listener at that time
     * @param readExceptionHandler the read exception handler to call if an error occurs during a read operation
     * @param writeExceptionHandler the write exception handler to call if an error occurs during a write operation
     * @param pool the pool from which the transfer buffer should be allocated
     */
    public static <I extends StreamSourceChannel, O extends StreamSinkChannel> void initiateTransfer(long count, final I source, final O sink, final ChannelListener<? super I> sourceListener, final ChannelListener<? super O> sinkListener, final ChannelExceptionHandler<? super I> readExceptionHandler, final ChannelExceptionHandler<? super O> writeExceptionHandler, Pool<ByteBuffer> pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }
        final Pooled<ByteBuffer> allocated = pool.allocate();
        boolean free = true;
        try {
            final ByteBuffer buffer = allocated.getResource();
            long transferred;
            do {
                try {
                    transferred = source.transferTo(count, buffer, sink);
                } catch (IOException e) {
                    invokeChannelExceptionHandler(source, readExceptionHandler, e);
                    return;
                }
                if (transferred == -1) {
                    if (count == Long.MAX_VALUE) {
                        ChannelListeners.invokeChannelListener(source, sourceListener);
                        ChannelListeners.invokeChannelListener(sink, sinkListener);
                    } else {
                        source.suspendReads();
                        sink.suspendWrites();
                        invokeChannelExceptionHandler(source, readExceptionHandler, new EOFException());
                    }
                    return;
                }
                if (count != Long.MAX_VALUE) {
                    count -= transferred;
                }
                if(count == 0) {
                    ChannelListeners.invokeChannelListener(source, sourceListener);
                    ChannelListeners.invokeChannelListener(sink, sinkListener);
                    return;
                }
                while (buffer.hasRemaining()) {
                    final int res;
                    try {
                        res = sink.write(buffer);
                        if (count != Long.MAX_VALUE) {
                            count -= res;
                        }
                    } catch (IOException e) {
                        invokeChannelExceptionHandler(sink, writeExceptionHandler, e);
                        return;
                    }
                    if (res == 0) {
                        // write first listener
                        final TransferListener<I, O> listener = new TransferListener<I, O>(count, allocated, source, sink, sourceListener, sinkListener, writeExceptionHandler, readExceptionHandler, 1);
                        source.suspendReads();
                        source.getReadSetter().set(listener);
                        sink.getWriteSetter().set(listener);
                        sink.resumeWrites();
                        free = false;
                        return;
                    } else if (res == -1) {
                        if (count == Long.MAX_VALUE || count == 0) {
                            ChannelListeners.invokeChannelListener(source, sourceListener);
                            ChannelListeners.invokeChannelListener(sink, sinkListener);
                        } else {
                            source.suspendReads();
                            sink.suspendWrites();
                            invokeChannelExceptionHandler(source, readExceptionHandler, new EOFException());
                        }
                        return;
                    }
                }
            } while (transferred > 0L);
            // read first listener
            final TransferListener<I, O> listener = new TransferListener<I, O>(count, allocated, source, sink, sourceListener, sinkListener, writeExceptionHandler, readExceptionHandler, 0);
            sink.suspendWrites();
            sink.getWriteSetter().set(listener);
            source.getReadSetter().set(listener);
            source.resumeReads();
            free = false;
            return;
        } finally {
            if (free) allocated.free();
        }
    }


    /**
     * Safely invoke a channel exception handler, logging any errors.
     *
     * @param channel the channel
     * @param exceptionHandler the exception handler
     * @param exception the exception to pass in
     * @param <T> the exception type
     */
    public static <T extends Channel> void invokeChannelExceptionHandler(final T channel, final ChannelExceptionHandler<? super T> exceptionHandler, final IOException exception) {
        try {
            exceptionHandler.handleException(channel, exception);
        } catch (Throwable t) {
            WebSocketLogger.REQUEST_LOGGER.errorf(t, "A channel exception handler threw an exception");
        }
    }


    static final class TransferListener<I extends StreamSourceChannel, O extends StreamSinkChannel> implements ChannelListener<Channel> {
        private final Pooled<ByteBuffer> pooledBuffer;
        private final I source;
        private final O sink;
        private final ChannelListener<? super I> sourceListener;
        private final ChannelListener<? super O> sinkListener;
        private final ChannelExceptionHandler<? super O> writeExceptionHandler;
        private final ChannelExceptionHandler<? super I> readExceptionHandler;
        private long count;
        private volatile int state;

        TransferListener(final long count, final Pooled<ByteBuffer> pooledBuffer, final I source, final O sink, final ChannelListener<? super I> sourceListener, final ChannelListener<? super O> sinkListener, final ChannelExceptionHandler<? super O> writeExceptionHandler, final ChannelExceptionHandler<? super I> readExceptionHandler, final int state) {
            this.count = count;
            this.pooledBuffer = pooledBuffer;
            this.source = source;
            this.sink = sink;
            this.sourceListener = sourceListener;
            this.sinkListener = sinkListener;
            this.writeExceptionHandler = writeExceptionHandler;
            this.readExceptionHandler = readExceptionHandler;
            this.state = state;
        }

        public void handleEvent(final Channel channel) {
            final ByteBuffer buffer = pooledBuffer.getResource();
            int state = this.state;
            // always read after and write before state
            long count = this.count;
            long lres;
            int ires;

            switch (state) {
                case 0: {
                    // read listener
                    for (;;) {
                        try {
                            lres = source.transferTo(count, buffer, sink);
                        } catch (IOException e) {
                            readFailed(e);
                            return;
                        }
                        if (lres == 0) {
                            this.count = count;
                            return;
                        }
                        if (lres == -1) {
                            // possibly unexpected EOF
                            if (count == Long.MAX_VALUE) {
                                // it's OK; just be done
                                done();
                                return;
                            } else {
                                readFailed(new EOFException());
                                return;
                            }
                        }
                        if (count != Long.MAX_VALUE) {
                            count -= lres;
                        }
                        while (buffer.hasRemaining()) {
                            try {
                                ires = sink.write(buffer);
                            } catch (IOException e) {
                                writeFailed(e);
                                return;
                            }
                            if (ires == 0) {
                                this.count = count;
                                this.state = 1;
                                source.suspendReads();
                                sink.resumeWrites();
                                return;
                            }
                        }
                    }
                }
                case 1: {
                    // write listener
                    for (;;) {
                        while (buffer.hasRemaining()) {
                            try {
                                ires = sink.write(buffer);
                            } catch (IOException e) {
                                writeFailed(e);
                                return;
                            }
                            if (ires == 0) {
                                return;
                            }
                        }
                        try {
                            lres = source.transferTo(count, buffer, sink);
                        } catch (IOException e) {
                            readFailed(e);
                            return;
                        }
                        if (lres == 0) {
                            this.count = count;
                            this.state = 0;
                            sink.suspendWrites();
                            source.resumeReads();
                            return;
                        }
                        if (lres == -1) {
                            // possibly unexpected EOF
                            if (count == Long.MAX_VALUE) {
                                // it's OK; just be done
                                done();
                                return;
                            } else {
                                readFailed(new EOFException());
                                return;
                            }
                        }
                        if (count != Long.MAX_VALUE) {
                            count -= lres;
                        }
                    }
                }
            }
        }

        private void writeFailed(final IOException e) {
            try {
                source.suspendReads();
                sink.suspendWrites();
                invokeChannelExceptionHandler(sink, writeExceptionHandler, e);
            } finally {
                pooledBuffer.free();
            }
        }

        private void readFailed(final IOException e) {
            try {
                source.suspendReads();
                sink.suspendWrites();
                invokeChannelExceptionHandler(source, readExceptionHandler, e);
            } finally {
                pooledBuffer.free();
            }
        }

        private void done() {
            try {
                final ChannelListener<? super I> sourceListener = this.sourceListener;
                final ChannelListener<? super O> sinkListener = this.sinkListener;
                final I source = this.source;
                final O sink = this.sink;

                Channels.setReadListener(source, sourceListener);
                if (sourceListener == null) {
                    source.suspendReads();
                } else {
                    source.wakeupReads();
                }

                Channels.setWriteListener(sink, sinkListener);
                if (sinkListener == null) {
                    sink.suspendWrites();
                } else {
                    sink.wakeupWrites();
                }
            } finally {
                pooledBuffer.free();
            }
        }

        public String toString() {
            return "Transfer channel listener (" + source + " to " + sink + ") -> (" + sourceListener + " and " + sinkListener + ")";
        }
    }
}
