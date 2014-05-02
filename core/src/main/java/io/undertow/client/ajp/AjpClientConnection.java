/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package io.undertow.client.ajp;

import io.undertow.UndertowLogger;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.UndertowClientMessages;
import io.undertow.conduits.ConduitListener;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.Protocols;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.PushBackStreamSourceConduit;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import static io.undertow.client.UndertowClientMessages.MESSAGES;
import static io.undertow.util.Headers.CLOSE;
import static io.undertow.util.Headers.CONNECTION;
import static io.undertow.util.Headers.CONTENT_LENGTH;
import static io.undertow.util.Headers.TRANSFER_ENCODING;
import static io.undertow.util.Headers.UPGRADE;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.IoUtils.safeClose;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class AjpClientConnection extends AbstractAttachable implements Closeable, ClientConnection {

    public final ConduitListener<StreamSinkConduit> requestFinishListener = new ConduitListener<StreamSinkConduit>() {
        @Override
        public void handleEvent(StreamSinkConduit channel) {
            currentRequest.terminateRequest();
        }
    };
    public final ConduitListener<StreamSourceConduit> responseFinishedListener = new ConduitListener<StreamSourceConduit>() {
        @Override
        public void handleEvent(StreamSourceConduit channel) {
            currentRequest.terminateResponse();
        }
    };

    private final Deque<AjpClientExchange> pendingQueue = new ArrayDeque<AjpClientExchange>();
    private AjpClientExchange currentRequest;
    private AjpResponseBuilder pendingResponse;

    private final OptionMap options;
    private final StreamConnection connection;
    private final PushBackStreamSourceConduit pushBackStreamSourceConduit;

    private final Pool<ByteBuffer> bufferPool;
    private final StreamSinkConduit originalSinkConduit;

    private static final int UPGRADED = 1 << 28;
    private static final int UPGRADE_REQUESTED = 1 << 29;
    private static final int CLOSE_REQ = 1 << 30;
    private static final int CLOSED = 1 << 31;

    private int state;

    private final ChannelListener.SimpleSetter<AjpClientConnection> closeSetter = new ChannelListener.SimpleSetter<AjpClientConnection>();
    private final ClientReadListener clientReadListener = new ClientReadListener();

    AjpClientConnection(final StreamConnection connection, final OptionMap options, final Pool<ByteBuffer> bufferPool) {
        this.options = options;
        this.connection = connection;
        this.pushBackStreamSourceConduit = new PushBackStreamSourceConduit(connection.getSourceChannel().getConduit());
        this.connection.getSourceChannel().setConduit(pushBackStreamSourceConduit);
        this.bufferPool = bufferPool;
        this.originalSinkConduit = connection.getSinkChannel().getConduit();

        connection.getCloseSetter().set(new ChannelListener<StreamConnection>() {

            public void handleEvent(StreamConnection channel) {
                ChannelListeners.invokeChannelListener(AjpClientConnection.this, closeSetter.get());
            }
        });
    }

    @Override
    public Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }


    @Override
    public SocketAddress getPeerAddress() {
        return connection.getPeerAddress();
    }

    StreamConnection getConnection() {
        return connection;
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        return connection.getPeerAddress(type);
    }

    @Override
    public ChannelListener.Setter<? extends AjpClientConnection> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return connection.getLocalAddress();
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        return connection.getLocalAddress(type);
    }

    @Override
    public XnioWorker getWorker() {
        return connection.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return connection.getIoThread();
    }

    @Override
    public boolean isOpen() {
        return connection.isOpen();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return connection.supportsOption(option);
    }


    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return connection.getOption(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return connection.setOption(option, value);
    }

    @Override
    public boolean isUpgraded() {
        return anyAreSet(state, UPGRADE_REQUESTED | UPGRADED);
    }

    @Override
    public void sendRequest(final ClientRequest request, final ClientCallback<ClientExchange> clientCallback) {
        if (anyAreSet(state, UPGRADE_REQUESTED | UPGRADED | CLOSE_REQ | CLOSED)) {
            clientCallback.failed(UndertowClientMessages.MESSAGES.invalidConnectionState());
            return;
        }
        final AjpClientExchange AjpClientExchange = new AjpClientExchange(clientCallback, request, this);
        if (currentRequest == null) {
            initiateRequest(AjpClientExchange);
        } else {
            pendingQueue.add(AjpClientExchange);
        }
    }

    private void initiateRequest(AjpClientExchange AjpClientExchange) {
        currentRequest = AjpClientExchange;
        pendingResponse = new AjpResponseBuilder();
        ClientRequest request = AjpClientExchange.getRequest();

        String connectionString = request.getRequestHeaders().getFirst(CONNECTION);
        if (connectionString != null) {
            if (CLOSE.equalToString(connectionString)) {
                state |= CLOSE_REQ;
            }
        } else if (request.getProtocol() != Protocols.HTTP_1_1) {
            state |= CLOSE_REQ;
        }
        if (request.getRequestHeaders().contains(UPGRADE)) {
            state |= UPGRADE_REQUESTED;
        }

        //setup the client request conduits
        final ConduitStreamSourceChannel sourceChannel = connection.getSourceChannel();
        sourceChannel.setReadListener(clientReadListener);
        sourceChannel.resumeReads();

        long length = 0;
        ConduitStreamSinkChannel sinkChannel = connection.getSinkChannel();
        String fixedLengthString = request.getRequestHeaders().getFirst(CONTENT_LENGTH);
        String transferEncodingString = request.getRequestHeaders().getLast(TRANSFER_ENCODING);

        if (fixedLengthString != null) {
            length = Long.parseLong(fixedLengthString);
        } else if (transferEncodingString != null) {
            length = -1;
        }
        final AjpClientRequestConduit ajpClientRequestConduit = new AjpClientRequestConduit(originalSinkConduit, bufferPool, currentRequest, requestFinishListener, length);
        currentRequest.setAjpClientRequestConduit(ajpClientRequestConduit);
        sinkChannel.setConduit(ajpClientRequestConduit);

        AjpClientExchange.invokeReadReadyCallback(AjpClientExchange);
        if (length == 0) {
            //if there is no content we flush the response channel.
            //otherwise it is up to the user
            try {
                sinkChannel.shutdownWrites();
                if (!sinkChannel.flush()) {
                    sinkChannel.setWriteListener(ChannelListeners.flushingChannelListener(null, new ChannelExceptionHandler<ConduitStreamSinkChannel>() {
                        @Override
                        public void handleException(ConduitStreamSinkChannel channel, IOException exception) {
                            handleError(exception);
                        }
                    }));
                }
            } catch (IOException e) {
                handleError(e);
            }
        } else if (!sinkChannel.isWriteResumed()) {
            try {
                //TODO: this needs some more thought
                if (!sinkChannel.flush()) {
                    sinkChannel.setWriteListener(new ChannelListener<ConduitStreamSinkChannel>() {
                        @Override
                        public void handleEvent(ConduitStreamSinkChannel channel) {
                            try {
                                if (channel.flush()) {
                                    channel.suspendWrites();
                                }
                            } catch (IOException e) {
                                handleError(e);
                            }
                        }
                    });
                    sinkChannel.resumeWrites();
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private void handleError(IOException exception) {
        currentRequest.setFailed(exception);
        IoUtils.safeClose(connection);
    }

    public StreamConnection performUpgrade() throws IOException {

        // Upgrade the connection
        // Set the upgraded flag already to prevent new requests after this one
        if (allAreSet(state, UPGRADED | CLOSE_REQ | CLOSED)) {
            throw new IOException(UndertowClientMessages.MESSAGES.connectionClosed());
        }
        state |= UPGRADED;
        return connection;
    }

    public void close() throws IOException {
        if (anyAreSet(state, CLOSED)) {
            return;
        }
        state |= CLOSED | CLOSE_REQ;
        connection.close();
    }

    /**
     * Notification that the current request is finished
     */
    public void requestDone() {

        connection.getSinkChannel().setConduit(originalSinkConduit);
        connection.getSourceChannel().setConduit(pushBackStreamSourceConduit);
        connection.getSinkChannel().suspendWrites();
        connection.getSinkChannel().setWriteListener(null);

        if (anyAreSet(state, CLOSE_REQ)) {
            currentRequest = null;
            IoUtils.safeClose(connection);
        } else if (anyAreSet(state, UPGRADE_REQUESTED)) {
            connection.getSourceChannel().suspendReads();
            currentRequest = null;
            return;
        }
        currentRequest = null;

        AjpClientExchange next = pendingQueue.poll();

        if (next == null) {
            connection.getSourceChannel().setReadListener(clientReadListener);
            connection.getSourceChannel().resumeReads();
        } else {
            initiateRequest(next);
        }
    }

    public void requestClose() {
        state |= CLOSE_REQ;
    }

    public void installReadBodyListener() {
        connection.getSourceChannel().setConduit(pushBackStreamSourceConduit);
        connection.getSourceChannel().setReadListener(new ResponseReceivedReadListener());
        connection.getSourceChannel().resumeReads();
    }

    class ClientReadListener implements ChannelListener<StreamSourceChannel> {

        public void handleEvent(StreamSourceChannel channel) {

            AjpResponseBuilder builder = pendingResponse;
            final Pooled<ByteBuffer> pooled = bufferPool.allocate();
            final ByteBuffer buffer = pooled.getResource();
            buffer.clear();
            boolean free = true;

            try {
                if (builder == null) {
                    //read ready when no request pending
                    buffer.clear();
                    try {
                        int res = channel.read(buffer);
                        if (res == -1) {
                            UndertowLogger.CLIENT_LOGGER.debugf("Connection to %s was closed by the target server", connection.getPeerAddress());
                            IoUtils.safeClose(AjpClientConnection.this);
                        } else if (res != 0) {
                            UndertowLogger.CLIENT_LOGGER.debugf("Target server %s sent unexpected data when no request pending, closing connection", connection.getPeerAddress());
                            IoUtils.safeClose(AjpClientConnection.this);
                        }
                        //otherwise it is a spurious notification
                    } catch (IOException e) {
                        if (UndertowLogger.CLIENT_LOGGER.isDebugEnabled()) {
                            UndertowLogger.CLIENT_LOGGER.debugf(e, "Connection closed with IOException");
                        }
                        safeClose(connection);
                    }
                    return;
                }
                final AjpResponseParseState state = builder.getParseState();
                int res;
                do {
                    try {
                        res = channel.read(buffer);
                    } catch (IOException e) {
                        if (UndertowLogger.CLIENT_LOGGER.isDebugEnabled()) {
                            UndertowLogger.CLIENT_LOGGER.debugf(e, "Connection closed with IOException");
                        }
                        safeClose(channel);
                        currentRequest.setFailed(new IOException(MESSAGES.connectionClosed()));
                        return;
                    }

                    buffer.flip();

                    if (res == 0 && !buffer.hasRemaining()) {
                        if (!channel.isReadResumed()) {
                            channel.getReadSetter().set(this);
                            channel.resumeReads();
                        }
                        return;
                    } else if (res == -1 && !buffer.hasRemaining()) {
                        channel.suspendReads();
                        IoUtils.safeClose(AjpClientConnection.this);
                        try {
                            final StreamSinkChannel requestChannel = connection.getSinkChannel();
                            requestChannel.shutdownWrites();
                            // will return false if there's a response queued ahead of this one, so we'll set up a listener then
                            if (!requestChannel.flush()) {
                                requestChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, null));
                                requestChannel.resumeWrites();
                            }
                            // Cancel the current active request
                            currentRequest.setFailed(new IOException(MESSAGES.connectionClosed()));
                        } catch (IOException e) {
                            if (UndertowLogger.CLIENT_LOGGER.isDebugEnabled()) {
                                UndertowLogger.CLIENT_LOGGER.debugf(e, "Connection closed with IOException when attempting to shut down reads");
                            }
                            // Cancel the current active request
                            currentRequest.setFailed(e);
                            IoUtils.safeClose(channel);
                            return;
                        }
                        return;
                    }

                    AjpResponseParser.INSTANCE.parse(buffer, state, builder);

                    //this is a bit hacky
                    //if the state=6 it is a ready body chunk response and not headers
                    //in which case we notify the conduit and reset the state
                    if (state.isComplete()) {
                        if (state.prefix == 6) {
                            currentRequest.getAjpClientRequestConduit().setBodyChunkRequested(state.currentIntegerPart);
                            state.reset();
                            buffer.compact();
                        } else if (buffer.hasRemaining()) {
                            free = false;
                            pushBackStreamSourceConduit.pushBack(pooled);
                        }
                    } else {
                        buffer.clear();
                    }

                } while (!state.isComplete());

                final ClientResponse response = builder.build();

                //check if an updated worked
                if (anyAreSet(AjpClientConnection.this.state, UPGRADE_REQUESTED)) {
                    String connectionString = response.getResponseHeaders().getFirst(CONNECTION);
                    if (connectionString == null || !UPGRADE.equalToString(connectionString)) {
                        //just unset the upgrade requested flag
                        AjpClientConnection.this.state &= ~UPGRADE_REQUESTED;
                    }
                }

                if (builder.getStatusCode() == 100) {
                    pendingResponse = new AjpResponseBuilder();
                    currentRequest.setContinueResponse(response);
                } else {
                    connection.getSourceChannel().setConduit(new AjpClientResponseConduit(connection.getSourceChannel().getConduit(), AjpClientConnection.this, currentRequest.getAjpClientRequestConduit(), responseFinishedListener));
                    channel.getReadSetter().set(null);
                    channel.suspendReads();
                    pendingResponse = null;
                    currentRequest.setResponse(response);
                }


            } catch (Exception e) {
                UndertowLogger.CLIENT_LOGGER.exceptionProcessingRequest(e);
                IoUtils.safeClose(connection);
                currentRequest.setFailed( e instanceof  IOException ? (IOException) e : new IOException(e));
            } finally {
                if (free) pooled.free();
            }
        }
    }

    /**
     * Listener that only listens for read body chunk messages, as even after the response is done the server
     * can still be reading the request.
     */
    class ResponseReceivedReadListener implements ChannelListener<StreamSourceChannel> {

        private AjpResponseBuilder builder = new AjpResponseBuilder();

        public void handleEvent(StreamSourceChannel channel) {

            final Pooled<ByteBuffer> pooled = bufferPool.allocate();
            final ByteBuffer buffer = pooled.getResource();
            buffer.clear();
            boolean free = true;

            try {
                final AjpResponseParseState state = builder.getParseState();
                int res;
                do {
                    try {
                        res = channel.read(buffer);
                    } catch (IOException e) {
                        if (UndertowLogger.CLIENT_LOGGER.isDebugEnabled()) {
                            UndertowLogger.CLIENT_LOGGER.debugf(e, "Connection closed with IOException");
                        }
                        safeClose(channel);
                        currentRequest.setFailed(new IOException(MESSAGES.connectionClosed()));
                        return;
                    }

                    buffer.flip();

                    if (res == 0 && !buffer.hasRemaining()) {
                        if (!channel.isReadResumed()) {
                            channel.getReadSetter().set(this);
                            channel.resumeReads();
                        }
                        return;
                    } else if (res == -1 && !buffer.hasRemaining()) {
                        channel.suspendReads();
                        IoUtils.safeClose(connection);
                        currentRequest.setFailed(new IOException(UndertowClientMessages.MESSAGES.connectionClosed()));
                        return;
                    }

                    AjpResponseParser.INSTANCE.parse(buffer, state, builder);

                    //this is a bit hacky
                    //if the state=6 it is a ready body chunk response and not headers
                    //in which case we notify the conduit and reset the state
                    if (state.isComplete()) {
                        if (state.prefix == 6) {
                            currentRequest.getAjpClientRequestConduit().setBodyChunkRequested(state.currentIntegerPart);
                            state.reset();
                            buffer.compact();
                        } else {
                            //todo: ping?
                            UndertowLogger.CLIENT_LOGGER.debugf("Received invalid AJP response code %s with no request active, closing connection", state.prefix);
                            //invalid, at this point read body chunk is all the server should be sending
                            IoUtils.safeClose(connection);
                            currentRequest.setFailed(UndertowClientMessages.MESSAGES.receivedInvalidChunk(state.prefix));
                        }
                    } else {
                        buffer.clear();
                    }

                } while (!state.isComplete());

            } catch (Exception e) {
                UndertowLogger.CLIENT_LOGGER.exceptionProcessingRequest(e);
                IoUtils.safeClose(connection);
            } finally {
                if (free) pooled.free();
            }
        }
    }

}
