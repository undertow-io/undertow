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
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.server.ConnectorStatisticsImpl;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.ParseTimeoutUpdater;
import io.undertow.server.protocol.http2.Http2ReceiveListener;
import io.undertow.util.ClosingChannelExceptionHandler;
import io.undertow.util.ConnectionUtils;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import io.undertow.util.StringWriteChannelListener;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
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

    /**
     * used for HTTP2 prior knowledge support
     */
    private static final HttpString PRI = new HttpString("PRI");
    private static final byte[] PRI_EXPECTED = new byte[] {'S', 'M', '\r', '\n', '\r', '\n'};


    private static final String BAD_REQUEST = "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";

    private final HttpServerConnection connection;
    private final ParseState state = new ParseState();
    private final HttpRequestParser parser;

    private HttpServerExchange httpServerExchange;

    private int read = 0;
    private final int maxRequestSize;
    private final long maxEntitySize;
    private final boolean recordRequestStartTime;
    private final boolean allowUnknownProtocols;
    private final boolean requireHostHeader;

    //0 = new request ok, reads resumed
    //1 = request running, new request not ok
    //2 = suspending/resuming in progress
    @SuppressWarnings("unused")
    private volatile int requestState;
    private static final AtomicIntegerFieldUpdater<HttpReadListener> requestStateUpdater = AtomicIntegerFieldUpdater.newUpdater(HttpReadListener.class, "requestState");

    private final ConnectorStatisticsImpl connectorStatistics;

    private ParseTimeoutUpdater parseTimeoutUpdater;

    HttpReadListener(final HttpServerConnection connection, final HttpRequestParser parser, ConnectorStatisticsImpl connectorStatistics) {
        this.connection = connection;
        this.parser = parser;
        this.connectorStatistics = connectorStatistics;
        this.maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);
        this.maxEntitySize = connection.getUndertowOptions().get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE);
        this.recordRequestStartTime = connection.getUndertowOptions().get(UndertowOptions.RECORD_REQUEST_START_TIME, false);
        this.requireHostHeader = connection.getUndertowOptions().get(UndertowOptions.REQUIRE_HOST_HTTP11, false);
        this.allowUnknownProtocols = connection.getUndertowOptions().get(UndertowOptions.ALLOW_UNKNOWN_PROTOCOLS, false);
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
        if(parseTimeoutUpdater != null) {
            parseTimeoutUpdater.connectionIdle();
        }
        connection.setCurrentExchange(null);
    }

    public void handleEvent(final ConduitStreamSourceChannel channel) {
        while (requestStateUpdater.get(this) != 0) {
            //if the CAS fails it is because another thread is in the process of changing state
            //we just immediately retry
            if (requestStateUpdater.compareAndSet(this, 1, 2)) {
                try {
                    channel.suspendReads();
                } finally {
                    requestStateUpdater.set(this, 1);
                }
                return;
            }
        }
        handleEventWithNoRunningRequest(channel);
    }

    public void handleEventWithNoRunningRequest(final ConduitStreamSourceChannel channel) {
        PooledByteBuffer existing = connection.getExtraBytes();
        if ((existing == null && connection.getOriginalSourceConduit().isReadShutdown()) || connection.getOriginalSinkConduit().isWriteShutdown()) {
            IoUtils.safeClose(connection);
            channel.suspendReads();
            return;
        }

        final PooledByteBuffer pooled = existing == null ? connection.getByteBufferPool().allocate() : existing;
        final ByteBuffer buffer = pooled.getBuffer();
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
                int begin = buffer.remaining();
                if(httpServerExchange == null) {
                    httpServerExchange = new HttpServerExchange(connection, maxEntitySize);
                }
                parser.handle(buffer, state, httpServerExchange);
                if (buffer.hasRemaining()) {
                    free = false;
                    connection.setExtraBytes(pooled);
                }
                int total = read + (begin - buffer.remaining());
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

            if(httpServerExchange.getProtocol() == Protocols.HTTP_2_0) {
                free = handleHttp2PriorKnowledge(pooled, httpServerExchange);
                return;
            }

            if(!allowUnknownProtocols) {
                HttpString protocol = httpServerExchange.getProtocol();
                if(protocol != Protocols.HTTP_1_1 && protocol != Protocols.HTTP_1_0 && protocol != Protocols.HTTP_0_9) {
                    UndertowLogger.REQUEST_IO_LOGGER.debugf("Closing connection from %s due to unknown protocol %s", connection.getChannel().getPeerAddress(), protocol);
                    sendBadRequestAndClose(connection.getChannel(), new IOException());
                    return;
                }
            }
            HttpTransferEncoding.setupRequest(httpServerExchange);
            if (recordRequestStartTime) {
                Connectors.setRequestStartTime(httpServerExchange);
            }
            connection.setCurrentExchange(httpServerExchange);
            if(connectorStatistics != null) {
                connectorStatistics.setup(httpServerExchange);
            }
            if(connection.getSslSession() != null) {
                //TODO: figure out a better solution for this
                //in order to improve performance we do not generally suspend reads, instead we a CAS to detect when
                //data arrives while a request is running and suspend lazily, as suspend/resume is relatively expensive
                //however this approach does not work for SSL, as the underlying channel is not thread safe
                //so we just suspend every time (the overhead is likely much less than the general SSL overhead anyway)
                channel.suspendReads();
            }

            if(requireHostHeader && !httpServerExchange.getRequestHeaders().contains(Headers.HOST)) {
                if(httpServerExchange.getProtocol().equals(Protocols.HTTP_1_1)) {
                    sendBadRequestAndClose(connection.getChannel(), UndertowMessages.MESSAGES.noHostInHttp11Request());
                    return;
                }
            }
            Connectors.executeRootHandler(connection.getRootHandler(), httpServerExchange);
        } catch (Exception e) {
            sendBadRequestAndClose(connection.getChannel(), e);
            return;
        } finally {
            if (free) pooled.close();
        }
    }

    private boolean handleHttp2PriorKnowledge(PooledByteBuffer pooled, HttpServerExchange httpServerExchange) throws IOException {
        if(httpServerExchange.getRequestMethod().equals(PRI) && connection.getUndertowOptions().get(UndertowOptions.ENABLE_HTTP2, false)) {
            handleHttp2PriorKnowledge(connection.getChannel(), connection, pooled);
            return false;
        } else {
            sendBadRequestAndClose(connection.getChannel(), new IOException());
            return true;
        }
    }

    private void handleFailedRead(ConduitStreamSourceChannel channel, int res) {
        if (res == 0) {
            channel.setReadListener(this);
            channel.resumeReads();
        } else if (res == -1) {
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
        if (exchange.isPersistent() && !isUpgradeOrConnect(exchange)) {
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
                                try {
                                    newRequest();
                                    channel.getSourceChannel().setReadListener(HttpReadListener.this);
                                    channel.getSourceChannel().resumeReads();
                                } finally {
                                    requestStateUpdater.set(this, 0);
                                }
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
                            try {
                                newRequest();
                                channel.getSourceChannel().suspendReads();
                            } finally {
                                requestStateUpdater.set(this, 0);
                            }
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
            ConnectionUtils.cleanClose(connection.getChannel(), connection);
        } else {
            //upgrade or connect handling
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

    private boolean isUpgradeOrConnect(HttpServerExchange exchange) {
        return exchange.isUpgrade() || (exchange.getRequestMethod().equals(Methods.CONNECT) && ((HttpServerConnection)exchange.getConnection()).isConnectHandled() );
    }

    @Override
    public void run() {
        handleEvent(connection.getChannel().getSourceChannel());
    }


    private void handleHttp2PriorKnowledge(final StreamConnection connection, final HttpServerConnection serverConnection, PooledByteBuffer readData) throws IOException {

        final ConduitStreamSourceChannel request = connection.getSourceChannel();

        byte[] data = new byte[PRI_EXPECTED.length];
        final ByteBuffer buffer = ByteBuffer.wrap(data);
        if(readData.getBuffer().hasRemaining()) {
            while (readData.getBuffer().hasRemaining() && buffer.hasRemaining()) {
                buffer.put(readData.getBuffer().get());
            }
        }
        final PooledByteBuffer extraData;
        if(readData.getBuffer().hasRemaining()) {
            extraData = readData;
        } else {
            readData.close();
            extraData = null;
        }
        if(!doHttp2PriRead(connection, buffer, serverConnection, extraData)) {
            request.getReadSetter().set(new ChannelListener<StreamSourceChannel>() {
                @Override
                public void handleEvent(StreamSourceChannel channel) {
                    try {
                        doHttp2PriRead(connection, buffer, serverConnection, extraData);
                    } catch (IOException e) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                        IoUtils.safeClose(connection);
                    }
                }
            });
            request.resumeReads();
        }
    }

    private boolean doHttp2PriRead(StreamConnection connection, ByteBuffer buffer, HttpServerConnection serverConnection, PooledByteBuffer extraData) throws IOException {
        if(buffer.hasRemaining()) {
            int res = connection.getSourceChannel().read(buffer);
            if (res == -1) {
                return true; //fail
            }
            if (buffer.hasRemaining()) {
                return false;
            }
        }
        buffer.flip();
        for(int i = 0; i < PRI_EXPECTED.length; ++i) {
            if(buffer.get() != PRI_EXPECTED[i]) {
                throw UndertowMessages.MESSAGES.http2PriRequestFailed();
            }
        }

        Http2Channel channel = new Http2Channel(connection, null, serverConnection.getByteBufferPool(), extraData, false, false, false, serverConnection.getUndertowOptions());
        Http2ReceiveListener receiveListener = new Http2ReceiveListener(serverConnection.getRootHandler(), serverConnection.getUndertowOptions(), serverConnection.getBufferSize(), null);
        channel.getReceiveSetter().set(receiveListener);
        channel.resumeReceives();
        return true;
    }
}
