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

package io.undertow.server.protocol.http;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.ParseTimeoutUpdater;
import io.undertow.util.ClosingChannelExceptionHandler;
import io.undertow.util.StringWriteChannelListener;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Listener which reads requests and headers off of an HTTP stream.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpReadListener implements ChannelListener<ConduitStreamSourceChannel>, Runnable {

    private static final String BAD_REQUEST = "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";

    private final HttpServerConnection connection;
    private final ParseState state = new ParseState();
    private final HttpRequestParser parser;

    private HttpServerExchange httpServerExchange;

    private int read = 0;
    private final int maxRequestSize;
    private final long maxEntitySize;
    private final boolean recordRequestStartTime;

    //0 = new request ok, reads resumed
    //1 = request running, new request not ok
    //2 = suspending/resuming in progress
    private volatile int requestState;

    private static final AtomicIntegerFieldUpdater<HttpReadListener> requestStateUpdater = AtomicIntegerFieldUpdater.newUpdater(HttpReadListener.class, "requestState");

    private ParseTimeoutUpdater parseTimeoutUpdater;

    HttpReadListener(final HttpServerConnection connection, final HttpRequestParser parser) {
        this.connection = connection;
        this.parser = parser;
        this.maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);
        this.maxEntitySize = connection.getUndertowOptions().get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE);
        this.recordRequestStartTime = connection.getUndertowOptions().get(UndertowOptions.RECORD_REQUEST_START_TIME, false);
        int requestParseTimeout = connection.getUndertowOptions().get(UndertowOptions.REQUEST_PARSE_TIMEOUT, -1);
        int requestIdleTimeout = connection.getUndertowOptions().get(UndertowOptions.NO_REQUEST_TIMEOUT, -1);
        if(requestIdleTimeout < 0 && requestParseTimeout < 0) {
            this.parseTimeoutUpdater = null;
        } else {
            this.parseTimeoutUpdater = new ParseTimeoutUpdater(connection, requestParseTimeout, requestIdleTimeout);
            connection.addCloseListener(parseTimeoutUpdater);
        }
    }

    public void newRequest() {
        state.reset();
        read = 0;
        httpServerExchange = new HttpServerExchange(connection, maxEntitySize);
        if(parseTimeoutUpdater != null) {
            parseTimeoutUpdater.connectionIdle();
        }
    }

    public void handleEvent(final ConduitStreamSourceChannel channel) {
        while (requestStateUpdater.get(this) != 0) {
            //if the CAS fails it is because another thread is in the process of changing state
            //we just immediately retry
            if (requestStateUpdater.compareAndSet(this, 1, 2)) {
                channel.suspendReads();
                requestStateUpdater.set(this, 1);
                return;
            }
        }
        handleEventWithNoRunningRequest(channel);
    }

    public void handleEventWithNoRunningRequest(final ConduitStreamSourceChannel channel) {
        Pooled<ByteBuffer> existing = connection.getExtraBytes();
        if ((existing == null && connection.getOriginalSourceConduit().isReadShutdown()) || connection.getOriginalSinkConduit().isWriteShutdown()) {
            IoUtils.safeClose(connection);
            channel.suspendReads();
            return;
        }

        final Pooled<ByteBuffer> pooled = existing == null ? connection.getBufferPool().allocate() : existing;
        final ByteBuffer buffer = pooled.getResource();
        boolean free = true;

        try {
            int res;
            boolean bytesRead = false;
            do {
                if (existing == null) {
                    buffer.clear();
                    try {
                        res = channel.read(buffer);
                    } catch (IOException e) {
                        UndertowLogger.REQUEST_IO_LOGGER.debug("Error reading request", e);
                        IoUtils.safeClose(connection);
                        return;
                    }
                } else {
                    res = buffer.remaining();
                }

                if (res <= 0) {
                    if(bytesRead && parseTimeoutUpdater != null) {
                        parseTimeoutUpdater.failedParse();
                    }
                    handleFailedRead(channel, res);
                    return;
                } else {
                    bytesRead = true;
                }
                if (existing != null) {
                    existing = null;
                    connection.setExtraBytes(null);
                } else {
                    buffer.flip();
                }
                parser.handle(buffer, state, httpServerExchange);
                if (buffer.hasRemaining()) {
                    free = false;
                    connection.setExtraBytes(pooled);
                }
                int total = read + res;
                read = total;
                if (read > maxRequestSize) {
                    UndertowLogger.REQUEST_LOGGER.requestHeaderWasTooLarge(connection.getPeerAddress(), maxRequestSize);
                    IoUtils.safeClose(connection);
                    return;
                }
            } while (!state.isComplete());
            if(parseTimeoutUpdater != null) {
                parseTimeoutUpdater.requestStarted();
            }

            final HttpServerExchange httpServerExchange = this.httpServerExchange;
            httpServerExchange.setRequestScheme(connection.getSslSession() != null ? "https" : "http");
            this.httpServerExchange = null;
            requestStateUpdater.set(this, 1);
            HttpTransferEncoding.setupRequest(httpServerExchange);
            if (recordRequestStartTime) {
                Connectors.setRequestStartTime(httpServerExchange);
            }
            connection.setCurrentExchange(httpServerExchange);
            Connectors.executeRootHandler(connection.getRootHandler(), httpServerExchange);
        } catch (Exception e) {
            sendBadRequestAndClose(connection.getChannel(), e);
            return;
        } finally {
            if (free) pooled.free();
        }
    }

    private void handleFailedRead(ConduitStreamSourceChannel channel, int res) {
        if (res == 0) {
            channel.setReadListener(this);
            channel.resumeReads();
        } else if (res == -1) {
            handleConnectionClose(channel);
        }
    }

    private void handleConnectionClose(StreamSourceChannel channel) {
        try {
            channel.suspendReads();
            channel.shutdownReads();
            final StreamSinkChannel responseChannel = this.connection.getChannel().getSinkChannel();
            responseChannel.shutdownWrites();
            IoUtils.safeClose(connection);
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.debug("Error reading request", e);
            // fuck it, it's all ruined
            IoUtils.safeClose(connection);
        }
    }

    private void sendBadRequestAndClose(final StreamConnection connection, final Exception exception) {
        UndertowLogger.REQUEST_IO_LOGGER.failedToParseRequest(exception);
        connection.getSourceChannel().suspendReads();
        new StringWriteChannelListener(BAD_REQUEST) {
            @Override
            protected void writeDone(final StreamSinkChannel c) {
                super.writeDone(c);
                c.suspendWrites();
                IoUtils.safeClose(connection);
            }

            @Override
            protected void handleError(StreamSinkChannel channel, IOException e) {
                IoUtils.safeClose(connection);
            }
        }.setup(connection.getSinkChannel());
    }

    public void exchangeComplete(final HttpServerExchange exchange) {
        connection.clearChannel();
        final HttpServerConnection connection = this.connection;
        if (exchange.isPersistent() && !exchange.isUpgrade()) {
            final StreamConnection channel = connection.getChannel();
            if (connection.getExtraBytes() == null) {
                //if we are not pipelining we just register a listener
                //we have to resume from with the io thread
                if (exchange.isInIoThread()) {
                    //no need for CAS, we are in the IO thread
                    newRequest();
                    channel.getSourceChannel().setReadListener(HttpReadListener.this);
                    channel.getSourceChannel().resumeReads();
                    requestStateUpdater.set(this, 0);
                } else {
                    while (true) {
                        if (connection.getOriginalSourceConduit().isReadShutdown() || connection.getOriginalSinkConduit().isWriteShutdown()) {
                            channel.getSourceChannel().suspendReads();
                            channel.getSinkChannel().suspendWrites();
                            IoUtils.safeClose(connection);
                            return;
                        } else {
                            if (requestStateUpdater.compareAndSet(this, 1, 2)) {
                                newRequest();
                                channel.getSourceChannel().setReadListener(HttpReadListener.this);
                                requestStateUpdater.set(this, 0);
                                channel.getSourceChannel().resumeReads();
                                break;
                            }
                        }
                    }
                }
            } else {
                if (exchange.isInIoThread()) {
                    requestStateUpdater.set(this, 0); //no need to CAS, as we don't actually resume
                    newRequest();
                    //no need to suspend reads here, the task will always run before the read listener anyway
                    channel.getIoThread().execute(this);
                } else {
                    while (true) {
                        if (connection.getOriginalSinkConduit().isWriteShutdown()) {
                            channel.getSourceChannel().suspendReads();
                            channel.getSinkChannel().suspendWrites();
                            IoUtils.safeClose(connection);
                            return;
                        } else if (requestStateUpdater.compareAndSet(this, 1, 2)) {
                            newRequest();
                            channel.getSourceChannel().suspendReads();
                            requestStateUpdater.set(this, 0);
                            break;
                        }
                    }
                    Executor executor = exchange.getDispatchExecutor();
                    if (executor == null) {
                        executor = exchange.getConnection().getWorker();
                    }
                    executor.execute(this);
                }
            }
        } else if (!exchange.isPersistent()) {
            IoUtils.safeClose(connection);
        } else if (exchange.isUpgrade()) {
            if (connection.getExtraBytes() != null) {
                connection.getChannel().getSourceChannel().setConduit(new ReadDataStreamSourceConduit(connection.getChannel().getSourceChannel().getConduit(), connection));
            }
            try {
                if (!connection.getChannel().getSinkChannel().flush()) {
                    connection.getChannel().getSinkChannel().setWriteListener(ChannelListeners.flushingChannelListener(new ChannelListener<ConduitStreamSinkChannel>() {
                        @Override
                        public void handleEvent(ConduitStreamSinkChannel conduitStreamSinkChannel) {
                            connection.getUpgradeListener().handleUpgrade(connection.getChannel(), exchange);
                        }
                    }, new ClosingChannelExceptionHandler<ConduitStreamSinkChannel>(connection)));
                    connection.getChannel().getSinkChannel().resumeWrites();
                    return;
                }
                connection.getUpgradeListener().handleUpgrade(connection.getChannel(), exchange);
            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(connection);
            }
        }
    }

    @Override
    public void run() {
        handleEvent(connection.getChannel().getSourceChannel());
    }
}
