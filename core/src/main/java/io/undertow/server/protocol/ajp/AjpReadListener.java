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

package io.undertow.server.protocol.ajp;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.conduits.ConduitListener;
import io.undertow.conduits.EmptyStreamSourceConduit;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.server.AbstractServerConnection;
import io.undertow.server.ConnectorStatisticsImpl;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.ParseTimeoutUpdater;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.xnio.ChannelListener;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.util.StatusCodes;
import io.undertow.util.BadRequestException;
import org.xnio.StreamConnection;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSourceConduit;
import org.xnio.conduits.WriteReadyHandler;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.xnio.IoUtils.safeClose;

/**
 * @author Stuart Douglas
 */

final class AjpReadListener implements ChannelListener<StreamSourceChannel> {

    private static final byte[] CPONG = {'A', 'B', 0, 1, 9};
    private static final byte[] SEND_HEADERS_INTERNAL_SERVER_ERROR_MSG = {'A', 'B', 0, 8, 4, (byte)((500 >> 8) & 0xFF) , (byte)(500 & 0xFF), 0, 0, '\0', 0, 0};
    private static final byte[] SEND_HEADERS_BAD_REQUEST_MSG = {'A', 'B', 0, 8, 4, (byte)((400 >> 8) & 0xFF) , (byte)(400 & 0xFF), 0, 0, '\0', 0, 0};
    private static final byte[] END_RESPONSE = {'A', 'B', 0, 2, 5, 1};

    private final AjpServerConnection connection;
    private final String scheme;
    private final boolean recordRequestStartTime;
    private AjpRequestParseState state = new AjpRequestParseState();
    private HttpServerExchange httpServerExchange;

    private volatile int read = 0;
    private final int maxRequestSize;
    private final long maxEntitySize;
    private final AjpRequestParser parser;
    private final ConnectorStatisticsImpl connectorStatistics;
    private WriteReadyHandler.ChannelListenerHandler<ConduitStreamSinkChannel> writeReadyHandler;

    private ParseTimeoutUpdater parseTimeoutUpdater;

    AjpReadListener(final AjpServerConnection connection, final String scheme, AjpRequestParser parser, ConnectorStatisticsImpl connectorStatistics) {
        this.connection = connection;
        this.scheme = scheme;
        this.parser = parser;
        this.connectorStatistics = connectorStatistics;
        this.maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);
        this.maxEntitySize = connection.getUndertowOptions().get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE);
        this.writeReadyHandler = new WriteReadyHandler.ChannelListenerHandler<>(connection.getChannel().getSinkChannel());
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

    public void startRequest() {
        connection.resetChannel();
        state = new AjpRequestParseState();
        read = 0;
        if(parseTimeoutUpdater != null) {
            parseTimeoutUpdater.connectionIdle();
        }
        connection.setCurrentExchange(null);
    }

    public void handleEvent(final StreamSourceChannel channel) {
        if(connection.getOriginalSinkConduit().isWriteShutdown() || connection.getOriginalSourceConduit().isReadShutdown()) {
            safeClose(connection);
            channel.suspendReads();
            return;
        }

        PooledByteBuffer existing = connection.getExtraBytes();

        final PooledByteBuffer pooled = existing == null ? connection.getByteBufferPool().allocate() : existing;
        final ByteBuffer buffer = pooled.getBuffer();
        boolean free = true;
        boolean bytesRead = false;
        try {
            int res;
            do {
                if (existing == null) {
                    buffer.clear();
                    res = channel.read(buffer);
                } else {
                    res = buffer.remaining();
                }
                if (res == 0) {

                    if(bytesRead && parseTimeoutUpdater != null) {
                        parseTimeoutUpdater.failedParse();
                    }
                    if (!channel.isReadResumed()) {
                        channel.getReadSetter().set(this);
                        channel.resumeReads();
                    }
                    return;
                }
                if (res == -1) {
                    channel.shutdownReads();
                    final StreamSinkChannel responseChannel = connection.getChannel().getSinkChannel();
                    responseChannel.shutdownWrites();
                    safeClose(connection);
                    return;
                }
                bytesRead = true;
                //TODO: we need to handle parse errors
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
                parser.parse(buffer, state, httpServerExchange);

                read += begin - buffer.remaining();
                if (buffer.hasRemaining()) {
                    free = false;
                    connection.setExtraBytes(pooled);
                }
                if (read > maxRequestSize) {
                    UndertowLogger.REQUEST_LOGGER.requestHeaderWasTooLarge(connection.getPeerAddress(), maxRequestSize);
                    safeClose(connection);
                    return;
                }
            } while (!state.isComplete());

            if(parseTimeoutUpdater != null) {
                parseTimeoutUpdater.requestStarted();
            }
            if (state.prefix != AjpRequestParser.FORWARD_REQUEST) {
                if (state.prefix == AjpRequestParser.CPING) {
                    UndertowLogger.REQUEST_LOGGER.debug("Received CPING, sending CPONG");
                    handleCPing();
                } else if (state.prefix == AjpRequestParser.CPONG) {
                    UndertowLogger.REQUEST_LOGGER.debug("Received CPONG, starting next request");
                    state = new AjpRequestParseState();
                    channel.getReadSetter().set(this);
                    channel.resumeReads();
                } else {
                    UndertowLogger.REQUEST_LOGGER.ignoringAjpRequestWithPrefixCode(state.prefix);
                    safeClose(connection);
                }
                return;
            }

            // we remove ourselves as the read listener from the channel;
            // if the http handler doesn't set any then reads will suspend, which is the right thing to do
            channel.getReadSetter().set(null);
            channel.suspendReads();

            final HttpServerExchange httpServerExchange = this.httpServerExchange;
            final AjpServerResponseConduit responseConduit = new AjpServerResponseConduit(connection.getChannel().getSinkChannel().getConduit(), connection.getByteBufferPool(), httpServerExchange, new ConduitListener<AjpServerResponseConduit>() {
                @Override
                public void handleEvent(AjpServerResponseConduit channel) {
                    Connectors.terminateResponse(httpServerExchange);
                }
            }, httpServerExchange.getRequestMethod().equals(Methods.HEAD));
            connection.getChannel().getSinkChannel().setConduit(responseConduit);
            connection.getChannel().getSourceChannel().setConduit(createSourceConduit(connection.getChannel().getSourceChannel().getConduit(), responseConduit, httpServerExchange));
            //we need to set the write ready handler. This allows the response conduit to wrap it
            responseConduit.setWriteReadyHandler(writeReadyHandler);

            connection.setSSLSessionInfo(state.createSslSessionInfo());
            httpServerExchange.setSourceAddress(state.createPeerAddress());
            httpServerExchange.setDestinationAddress(state.createDestinationAddress());
            if(scheme != null) {
                httpServerExchange.setRequestScheme(scheme);
            }
            if(state.attributes != null) {
                httpServerExchange.putAttachment(HttpServerExchange.REQUEST_ATTRIBUTES, state.attributes);
            }
            AjpRequestParseState oldState = state;
            state = null;
            this.httpServerExchange = null;
            httpServerExchange.setPersistent(true);

            if(recordRequestStartTime) {
                Connectors.setRequestStartTime(httpServerExchange);
            }
            connection.setCurrentExchange(httpServerExchange);
            if(connectorStatistics != null) {
                connectorStatistics.setup(httpServerExchange);
            }
            if(!Connectors.areRequestHeadersValid(httpServerExchange.getRequestHeaders())) {
                oldState.badRequest = true;
                UndertowLogger.REQUEST_IO_LOGGER.debugf("Invalid AJP request from %s, request contained invalid headers", connection.getPeerAddress());
            }

            if(oldState.badRequest) {
                httpServerExchange.setStatusCode(StatusCodes.BAD_REQUEST);
                httpServerExchange.endExchange();
                handleBadRequest();
                safeClose(connection);
            } else {
                Connectors.executeRootHandler(connection.getRootHandler(), httpServerExchange);
            }
        } catch (BadRequestException e) {
            UndertowLogger.REQUEST_IO_LOGGER.failedToParseRequest(e);
            handleBadRequest();
            safeClose(connection);
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            handleInternalServerError();
            safeClose(connection);
        } catch (Throwable t) {
            UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(t);
            handleInternalServerError();
            safeClose(connection);
        } finally {
            if (free) pooled.close();
        }
    }

    private void handleInternalServerError() {
        sendMessages(SEND_HEADERS_INTERNAL_SERVER_ERROR_MSG, END_RESPONSE);
    }

    private void handleBadRequest() {
        sendMessages(SEND_HEADERS_BAD_REQUEST_MSG, END_RESPONSE);
    }

    private void handleCPing() {
        if (sendMessages(CPONG)) {
            AjpReadListener.this.handleEvent(connection.getChannel().getSourceChannel());
        }
    }

    private boolean sendMessages(final byte[]... rawMessages) {
        state = new AjpRequestParseState();
        final StreamConnection underlyingChannel = connection.getChannel();
        underlyingChannel.getSourceChannel().suspendReads();
        // detect buffer size
        int bufferSize = 0;
        for (int i = 0; i < rawMessages.length; i++) {
            bufferSize += rawMessages[i].length;
        }
        // fill in buffer
        final ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        for (int i = 0; i < rawMessages.length; i++) {
            buffer.put(rawMessages[i]);
        }
        buffer.flip();
        // send buffer content
        int res;
        try {
            do {
                res = underlyingChannel.getSinkChannel().write(buffer);
                if (res == 0) {
                    underlyingChannel.getSinkChannel().setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                        @Override
                        public void handleEvent(ConduitStreamSinkChannel channel) {
                            int res;
                            do {
                                try {
                                    res = channel.write(buffer);
                                    if (res == 0) {
                                        return;
                                    }
                                } catch (IOException e) {
                                    UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                                    safeClose(connection);
                                }
                            } while (buffer.hasRemaining());
                            channel.suspendWrites();
                            AjpReadListener.this.handleEvent(underlyingChannel.getSourceChannel());
                        }
                    });
                    underlyingChannel.getSinkChannel().resumeWrites();
                    return false;
                }
            } while (buffer.hasRemaining());
            return true;
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            safeClose(connection);
            return false;
        }
    }

    public void exchangeComplete(final HttpServerExchange exchange) {
        if (!exchange.isUpgrade() && exchange.isPersistent()) {
            startRequest();
            ConduitStreamSourceChannel channel = ((AjpServerConnection) exchange.getConnection()).getChannel().getSourceChannel();
            channel.getReadSetter().set(this);
            channel.wakeupReads();
        } else if(!exchange.isPersistent()) {
            safeClose(exchange.getConnection());
        }
    }

    private StreamSourceConduit createSourceConduit(StreamSourceConduit underlyingConduit, AjpServerResponseConduit responseConduit, final HttpServerExchange exchange) throws BadRequestException {

        ReadDataStreamSourceConduit conduit = new ReadDataStreamSourceConduit(underlyingConduit, (AbstractServerConnection) exchange.getConnection());

        final HeaderMap requestHeaders = exchange.getRequestHeaders();
        HttpString transferEncoding = Headers.IDENTITY;
        Long length;
        final String teHeader = requestHeaders.getLast(Headers.TRANSFER_ENCODING);
        boolean hasTransferEncoding = teHeader != null;
        if (hasTransferEncoding) {
            transferEncoding = new HttpString(teHeader);
        }
        final String requestContentLength = requestHeaders.getFirst(Headers.CONTENT_LENGTH);
        if (hasTransferEncoding && !transferEncoding.equals(Headers.IDENTITY)) {
            length = null; //unknown length
        } else if (requestContentLength != null) {
            try {
                final long contentLength = Long.parseLong(requestContentLength);
                if (contentLength == 0L) {
                    UndertowLogger.REQUEST_LOGGER.trace("No content, starting next request");
                    // no content - immediately start the next request, returning an empty stream for this one
                    Connectors.terminateRequest(httpServerExchange);
                    return new EmptyStreamSourceConduit(conduit.getReadThread());
                } else {
                    length = contentLength;
                }
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid Content-Length header", e);
            }
        } else {
            UndertowLogger.REQUEST_LOGGER.trace("No content length or transfer coding, starting next request");
            // no content - immediately start the next request, returning an empty stream for this one
            Connectors.terminateRequest(exchange);
            return new EmptyStreamSourceConduit(conduit.getReadThread());
        }
        return new AjpServerRequestConduit(conduit, exchange, responseConduit, length, new ConduitListener<AjpServerRequestConduit>() {
            @Override
            public void handleEvent(AjpServerRequestConduit channel) {
                Connectors.terminateRequest(exchange);
            }
        });
    }

}
