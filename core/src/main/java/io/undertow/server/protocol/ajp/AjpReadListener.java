package io.undertow.server.protocol.ajp;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.conduits.ConduitListener;
import io.undertow.conduits.EmptyStreamSourceConduit;
import io.undertow.conduits.ReadDataStreamSourceConduit;
import io.undertow.server.AbstractServerConnection;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.xnio.ChannelListener;
import org.xnio.Pooled;
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

    private static final byte[] CPONG = {'A', 'B', 0, 1, 9}; //CPONG response data

    private final AjpServerConnection connection;
    private final String scheme;
    private final boolean recordRequestStartTime;
    private AjpRequestParseState state = new AjpRequestParseState();
    private HttpServerExchange httpServerExchange;

    private volatile int read = 0;
    private final int maxRequestSize;
    private final long maxEntitySize;
    private final AjpRequestParser parser;
    private WriteReadyHandler.ChannelListenerHandler<ConduitStreamSinkChannel> writeReadyHandler;


    AjpReadListener(final AjpServerConnection connection, final String scheme, AjpRequestParser parser) {
        this.connection = connection;
        this.scheme = scheme;
        this.parser = parser;
        this.maxRequestSize = connection.getUndertowOptions().get(UndertowOptions.MAX_HEADER_SIZE, UndertowOptions.DEFAULT_MAX_HEADER_SIZE);
        this.maxEntitySize = connection.getUndertowOptions().get(UndertowOptions.MAX_ENTITY_SIZE, 0);
        this.writeReadyHandler = new WriteReadyHandler.ChannelListenerHandler<ConduitStreamSinkChannel>(connection.getChannel().getSinkChannel());
        this.recordRequestStartTime = connection.getUndertowOptions().get(UndertowOptions.RECORD_REQUEST_START_TIME, false);
    }

    public void startRequest() {
        connection.resetChannel();
        state = new AjpRequestParseState();
        httpServerExchange = new HttpServerExchange(connection, maxEntitySize);
        read = 0;
    }

    public void handleEvent(final StreamSourceChannel channel) {
        if(connection.getOriginalSinkConduit().isWriteShutdown() || connection.getOriginalSourceConduit().isReadShutdown()) {
            safeClose(connection);
            channel.suspendReads();
            return;
        }
        Pooled<ByteBuffer> existing = connection.getExtraBytes();

        final Pooled<ByteBuffer> pooled = existing == null ? connection.getBufferPool().allocate() : existing;
        final ByteBuffer buffer = pooled.getResource();
        boolean free = true;

        try {
            int res;
            do {
                if (existing == null) {
                    buffer.clear();
                    try {
                        res = channel.read(buffer);
                    } catch (IOException e) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                        safeClose(channel);
                        return;
                    }
                } else {
                    res = buffer.remaining();
                }
                if (res == 0) {
                    if (!channel.isReadResumed()) {
                        channel.getReadSetter().set(this);
                        channel.resumeReads();
                    }
                    return;
                }
                if (res == -1) {
                    try {
                        channel.shutdownReads();
                        final StreamSinkChannel responseChannel = connection.getChannel().getSinkChannel();
                        responseChannel.shutdownWrites();
                        safeClose(connection);
                    } catch (IOException e) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                        // fuck it, it's all ruined
                        safeClose(connection);
                        return;
                    }
                    return;
                }
                //TODO: we need to handle parse errors
                if (existing != null) {
                    existing = null;
                    connection.setExtraBytes(null);
                } else {
                    buffer.flip();
                }
                int begin = buffer.remaining();
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
            final AjpServerResponseConduit responseConduit = new AjpServerResponseConduit(connection.getChannel().getSinkChannel().getConduit(), connection.getBufferPool(), httpServerExchange, new ConduitListener<AjpServerResponseConduit>() {
                @Override
                public void handleEvent(AjpServerResponseConduit channel) {
                    Connectors.terminateResponse(httpServerExchange);
                }
            }, httpServerExchange.getRequestMethod().equals(Methods.HEAD));
            connection.getChannel().getSinkChannel().setConduit(responseConduit);
            connection.getChannel().getSourceChannel().setConduit(createSourceConduit(connection.getChannel().getSourceChannel().getConduit(), responseConduit, httpServerExchange));
            //we need to set the write ready handler. This allows the response conduit to wrap it
            responseConduit.setWriteReadyHandler(writeReadyHandler);

            try {
                connection.setSSLSessionInfo(state.createSslSessionInfo());
                httpServerExchange.setSourceAddress(state.createPeerAddress());
                httpServerExchange.setDestinationAddress(state.createDestinationAddress());
                if(scheme != null) {
                    httpServerExchange.setRequestScheme(scheme);
                }
                state = null;
                this.httpServerExchange = null;
                httpServerExchange.setPersistent(true);

                if(recordRequestStartTime) {
                    Connectors.setRequestStartTime(httpServerExchange);
                }
                Connectors.executeRootHandler(connection.getRootHandler(), httpServerExchange);

            } catch (Throwable t) {
                //TODO: we should attempt to return a 500 status code in this situation
                UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(t);
                safeClose(channel);
                safeClose(connection);
            }
        } catch (Exception e) {
            UndertowLogger.REQUEST_LOGGER.exceptionProcessingRequest(e);
            safeClose(connection.getChannel());
        } finally {
            if (free) pooled.free();
        }
    }

    private void handleCPing() {
        state = new AjpRequestParseState();
        final StreamConnection underlyingChannel = connection.getChannel();
        underlyingChannel.getSourceChannel().suspendReads();
        final ByteBuffer buffer = ByteBuffer.wrap(CPONG);
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
                    return;
                }
            } while (buffer.hasRemaining());
            AjpReadListener.this.handleEvent(underlyingChannel.getSourceChannel());
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            safeClose(connection);
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

    private StreamSourceConduit createSourceConduit(StreamSourceConduit underlyingConduit, AjpServerResponseConduit responseConduit, final HttpServerExchange exchange) {

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
            final long contentLength = Long.parseLong(requestContentLength);
            if (contentLength == 0L) {
                UndertowLogger.REQUEST_LOGGER.trace("No content, starting next request");
                // no content - immediately start the next request, returning an empty stream for this one
                Connectors.terminateRequest(httpServerExchange);
                return new EmptyStreamSourceConduit(conduit.getReadThread());
            } else {
                length = contentLength;
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
