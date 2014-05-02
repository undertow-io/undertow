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

package io.undertow.client.http;

import io.undertow.UndertowLogger;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.UndertowClientMessages;
import io.undertow.conduits.ChunkedStreamSinkConduit;
import io.undertow.conduits.ChunkedStreamSourceConduit;
import io.undertow.conduits.ConduitListener;
import io.undertow.conduits.FixedLengthStreamSourceConduit;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
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
import java.util.Locale;

import static io.undertow.client.UndertowClientMessages.MESSAGES;
import static io.undertow.util.Headers.CLOSE;
import static io.undertow.util.Headers.CONNECTION;
import static io.undertow.util.Headers.CONTENT_LENGTH;
import static io.undertow.util.Headers.TRANSFER_ENCODING;
import static io.undertow.util.Headers.UPGRADE;
import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.IoUtils.safeClose;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class HttpClientConnection extends AbstractAttachable implements Closeable, ClientConnection {

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

    private final Deque<HttpClientExchange> pendingQueue = new ArrayDeque<HttpClientExchange>();
    private HttpClientExchange currentRequest;
    private HttpResponseBuilder pendingResponse;

    private final OptionMap options;
    private final StreamConnection connection;
    private final PushBackStreamSourceConduit pushBackStreamSourceConduit;
    private final ClientReadListener clientReadListener = new ClientReadListener();

    private final Pool<ByteBuffer> bufferPool;
    private final StreamSinkConduit originalSinkConduit;

    private static final int UPGRADED = 1 << 28;
    private static final int UPGRADE_REQUESTED = 1 << 29;
    private static final int CLOSE_REQ = 1 << 30;
    private static final int CLOSED = 1 << 31;
    private int count = 0;

    private int state;

    private final ChannelListener.SimpleSetter<HttpClientConnection> closeSetter = new ChannelListener.SimpleSetter<HttpClientConnection>();

    HttpClientConnection(final StreamConnection connection, final OptionMap options, final Pool<ByteBuffer> bufferPool) {
        this.options = options;
        this.connection = connection;
        this.pushBackStreamSourceConduit = new PushBackStreamSourceConduit(connection.getSourceChannel().getConduit());
        this.connection.getSourceChannel().setConduit(pushBackStreamSourceConduit);
        this.bufferPool = bufferPool;
        this.originalSinkConduit = connection.getSinkChannel().getConduit();

        connection.getCloseSetter().set(new ChannelListener<StreamConnection>() {

            public void handleEvent(StreamConnection channel) {
                HttpClientConnection.this.state |= CLOSED;
                ChannelListeners.invokeChannelListener(HttpClientConnection.this, closeSetter.get());
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
    public ChannelListener.Setter<? extends HttpClientConnection> getCloseSetter() {
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
        return connection.isOpen() && allAreClear(state, CLOSE_REQ | CLOSED);
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
        count++;
        if (anyAreSet(state, UPGRADE_REQUESTED | UPGRADED | CLOSE_REQ | CLOSED)) {
            clientCallback.failed(UndertowClientMessages.MESSAGES.invalidConnectionState());
            return;
        }
        final HttpClientExchange httpClientExchange = new HttpClientExchange(clientCallback, request, this);
        if (currentRequest == null) {
            initiateRequest(httpClientExchange);
        } else {
            pendingQueue.add(httpClientExchange);
        }
    }

    private void initiateRequest(HttpClientExchange httpClientExchange) {
        currentRequest = httpClientExchange;
        pendingResponse = new HttpResponseBuilder();
        ClientRequest request = httpClientExchange.getRequest();

        String connectionString = request.getRequestHeaders().getFirst(CONNECTION);
        if (connectionString != null) {
            HttpString connectionHttpString = new HttpString(connectionString);
            if (connectionHttpString.equals(CLOSE)) {
                state |= CLOSE_REQ;
            } else if(connectionHttpString.equals(UPGRADE)) {
                state |= UPGRADE_REQUESTED;
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

        ConduitStreamSinkChannel sinkChannel = connection.getSinkChannel();
        StreamSinkConduit conduit = originalSinkConduit;
        conduit = new HttpRequestConduit(conduit, bufferPool, request);

        String fixedLengthString = request.getRequestHeaders().getFirst(CONTENT_LENGTH);
        String transferEncodingString = request.getRequestHeaders().getLast(TRANSFER_ENCODING);

        boolean hasContent = true;

        if (fixedLengthString != null) {
            try {
                long length = Long.parseLong(fixedLengthString);
                conduit = new ClientFixedLengthStreamSinkConduit(conduit, length, false, false, currentRequest);
                hasContent = length != 0;
            } catch (NumberFormatException e) {
                handleError(new IOException(e));
                return;
            }
        } else if (transferEncodingString != null) {
            if (!transferEncodingString.toLowerCase(Locale.ENGLISH).contains(Headers.CHUNKED.toString())) {
                handleError(UndertowClientMessages.MESSAGES.unknownTransferEncoding(transferEncodingString));
                return;
            }
            conduit = new ChunkedStreamSinkConduit(conduit, httpClientExchange.getConnection().getBufferPool(), false, false, httpClientExchange.getRequest().getRequestHeaders(), requestFinishListener, httpClientExchange);
        } else {
            conduit = new ClientFixedLengthStreamSinkConduit(conduit, 0, false, false, currentRequest);
            hasContent = false;
        }
        sinkChannel.setConduit(conduit);

        httpClientExchange.invokeReadReadyCallback(httpClientExchange);
        if (!hasContent) {
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
        UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
        safeClose(connection);
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
            this.state |= CLOSED;
            IoUtils.safeClose(connection);
        } else if (anyAreSet(state, UPGRADE_REQUESTED)) {
            connection.getSourceChannel().suspendReads();
            currentRequest = null;
            return;
        }
        currentRequest = null;

        HttpClientExchange next = pendingQueue.poll();

        if (next == null) {
            //we resume reads, so if the target goes away we get notified
            connection.getSourceChannel().setReadListener(clientReadListener);
            connection.getSourceChannel().resumeReads();
        } else {
            initiateRequest(next);
        }
    }

    class ClientReadListener implements ChannelListener<StreamSourceChannel> {

        public void handleEvent(StreamSourceChannel channel) {

            HttpResponseBuilder builder = pendingResponse;
            final Pooled<ByteBuffer> pooled = bufferPool.allocate();
            final ByteBuffer buffer = pooled.getResource();
            boolean free = true;

            try {

                if (builder == null) {
                    //read ready when no request pending
                    buffer.clear();
                    try {
                        int res = channel.read(buffer);
                         if(res == -1) {
                            UndertowLogger.CLIENT_LOGGER.debugf("Connection to %s was closed by the target server", connection.getPeerAddress());
                            IoUtils.safeClose(HttpClientConnection.this);
                        } else if(res != 0) {
                             UndertowLogger.CLIENT_LOGGER.debugf("Target server %s sent unexpected data when no request pending, closing connection", connection.getPeerAddress());
                             IoUtils.safeClose(HttpClientConnection.this);
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
                final ResponseParseState state = builder.getParseState();
                int res;
                do {
                    buffer.clear();
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

                    if (res == 0) {
                        if (!channel.isReadResumed()) {
                            channel.getReadSetter().set(this);
                            channel.resumeReads();
                        }
                        return;
                    } else if (res == -1) {
                        channel.suspendReads();
                        IoUtils.safeClose(HttpClientConnection.this);
                        // Cancel the current active request
                        currentRequest.setFailed(new IOException(MESSAGES.connectionClosed()));
                        return;
                    }

                    buffer.flip();

                    HttpResponseParser.INSTANCE.handle(buffer, state, builder);
                    if (buffer.hasRemaining()) {
                        free = false;
                        pushBackStreamSourceConduit.pushBack(pooled);
                    }

                } while (!state.isComplete());

                final ClientResponse response = builder.build();

                String connectionString = response.getResponseHeaders().getFirst(CONNECTION);

                //check if an upgrade worked
                if (anyAreSet(HttpClientConnection.this.state, UPGRADE_REQUESTED)) {
                    if ((connectionString == null || !UPGRADE.equalToString(connectionString)) && !response.getResponseHeaders().contains(UPGRADE)) {
                        //just unset the upgrade requested flag
                        HttpClientConnection.this.state &= ~UPGRADE_REQUESTED;
                    }
                }

                if(connectionString != null) {
                    if (HttpString.tryFromString(connectionString).equals(Headers.CLOSE)) {
                        HttpClientConnection.this.state |= CLOSE_REQ;
                    }
                }

                if (builder.getStatusCode() == 100) {
                    pendingResponse = new HttpResponseBuilder();
                    currentRequest.setContinueResponse(response);
                } else {
                    prepareResponseChannel(response, currentRequest);
                    channel.getReadSetter().set(null);
                    channel.suspendReads();
                    pendingResponse = null;
                    currentRequest.setResponse(response);
                }


            } catch (Exception e) {
                UndertowLogger.CLIENT_LOGGER.exceptionProcessingRequest(e);
                IoUtils.safeClose(connection);
                currentRequest.setFailed(new IOException(e));
            } finally {
                if (free) pooled.free();
            }


        }
    }

    private void prepareResponseChannel(ClientResponse response, ClientExchange exchange) {
        String encoding = response.getResponseHeaders().getLast(TRANSFER_ENCODING);
        boolean chunked = encoding != null && Headers.CHUNKED.equals(new HttpString(encoding));
        String length = response.getResponseHeaders().getFirst(CONTENT_LENGTH);
        if (exchange.getRequest().getMethod().equals(Methods.HEAD)) {
            connection.getSourceChannel().setConduit(new FixedLengthStreamSourceConduit(connection.getSourceChannel().getConduit(), 0, responseFinishedListener));
        } else if (chunked) {
            connection.getSourceChannel().setConduit(new ChunkedStreamSourceConduit(connection.getSourceChannel().getConduit(), pushBackStreamSourceConduit, bufferPool, responseFinishedListener, exchange));
        } else if (length != null) {
            try {
                long contentLength = Long.parseLong(length);
                connection.getSourceChannel().setConduit(new FixedLengthStreamSourceConduit(connection.getSourceChannel().getConduit(), contentLength, responseFinishedListener));
            } catch (NumberFormatException e) {
                handleError(new IOException(e));
                throw e;
            }
        } else if (response.getProtocol().equals(Protocols.HTTP_1_1)) {
            connection.getSourceChannel().setConduit(new FixedLengthStreamSourceConduit(connection.getSourceChannel().getConduit(), 0, responseFinishedListener));
        } else {
            state |= CLOSE_REQ;
        }
    }
}
