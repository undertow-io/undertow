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

package io.undertow.client.http;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.ClientStatistics;
import io.undertow.client.UndertowClientMessages;
import io.undertow.client.http2.Http2ClearClientProvider;
import io.undertow.client.http2.Http2ClientConnection;
import io.undertow.conduits.ByteActivityCallback;
import io.undertow.conduits.BytesReceivedStreamSourceConduit;
import io.undertow.conduits.BytesSentStreamSinkConduit;
import io.undertow.conduits.ChunkedStreamSinkConduit;
import io.undertow.conduits.ChunkedStreamSourceConduit;
import io.undertow.conduits.ConduitListener;
import io.undertow.conduits.FixedLengthStreamSourceConduit;
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.server.Connectors;
import io.undertow.server.protocol.http.HttpContinue;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.ConnectionUtils;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.PooledAdaptor;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.PushBackStreamSourceConduit;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceConduit;
import org.xnio.ssl.SslConnection;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

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

    private final Deque<HttpClientExchange> pendingQueue = new ArrayDeque<>();
    private HttpClientExchange currentRequest;
    private HttpResponseBuilder pendingResponse;

    private final OptionMap options;
    private final StreamConnection connection;
    private final PushBackStreamSourceConduit pushBackStreamSourceConduit;
    private final ClientReadListener clientReadListener = new ClientReadListener();

    private final ByteBufferPool bufferPool;
    private PooledByteBuffer pooledBuffer;
    private final StreamSinkConduit originalSinkConduit;

    private static final int UPGRADED = 1 << 28;
    private static final int UPGRADE_REQUESTED = 1 << 29;
    private static final int CLOSE_REQ = 1 << 30;
    private static final int CLOSED = 1 << 31;
    private int count = 0;

    private int state;
    private final ChannelListener.SimpleSetter<HttpClientConnection> closeSetter = new ChannelListener.SimpleSetter<>();

    private final ClientStatistics clientStatistics;
    private int requestCount;
    private int read, written;
    private boolean http2Tried = false;
    private boolean http2UpgradeReceived = false;

    /**
     * The actual connection if this has been upgraded to h2c
     */
    private ClientConnection http2Delegate;
    private final List<ChannelListener<ClientConnection>> closeListeners = new CopyOnWriteArrayList<>();

    HttpClientConnection(final StreamConnection connection, final OptionMap options, final ByteBufferPool bufferPool) {

        //first we set up statistics, if required
        if(options.get(UndertowOptions.ENABLE_STATISTICS, false)) {
            clientStatistics = new ClientStatisticsImpl();
            connection.getSinkChannel().setConduit(new BytesSentStreamSinkConduit(connection.getSinkChannel().getConduit(), new ByteActivityCallback() {
                @Override
                public void activity(long bytes) {
                    written+=bytes;
                }
            }));
            connection.getSourceChannel().setConduit(new BytesReceivedStreamSourceConduit(connection.getSourceChannel().getConduit(), new ByteActivityCallback() {
                @Override
                public void activity(long bytes) {
                    read+=bytes;
                }
            }));
        } else {
            clientStatistics = null;
        }
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
                try {
                    if (pooledBuffer != null) {
                        pooledBuffer.close();
                    }
                } catch (Throwable ignored){}

                for(ChannelListener<ClientConnection> listener : closeListeners) {
                    listener.handleEvent(HttpClientConnection.this);
                }
            }
        });
    }

    @Override
    public ByteBufferPool getBufferPool() {
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
        if(http2Delegate != null) {
            return http2Delegate.isOpen();
        }
        return connection.isOpen() && allAreClear(state, CLOSE_REQ | CLOSED);
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        if(http2Delegate != null) {
            return http2Delegate.supportsOption(option);
        }
        return connection.supportsOption(option);
    }


    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        if(http2Delegate != null) {
            return http2Delegate.getOption(option);
        }
        return connection.getOption(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        if(http2Delegate != null) {
            return http2Delegate.setOption(option, value);
        }
        return connection.setOption(option, value);
    }

    @Override
    public boolean isUpgraded() {
        if(http2Delegate != null) {
            return http2Delegate.isUpgraded();
        }
        return anyAreSet(state, UPGRADE_REQUESTED | UPGRADED);
    }

    @Override
    public boolean isPushSupported() {
        if(http2Delegate != null) {
            return http2Delegate.isPushSupported();
        }
        return false;
    }

    @Override
    public boolean isMultiplexingSupported() {
        if(http2Delegate != null) {
            return http2Delegate.isMultiplexingSupported();
        }
        return false;
    }

    @Override
    public ClientStatistics getStatistics() {
        if(http2Delegate != null) {
            return http2Delegate.getStatistics();
        }
        return clientStatistics;
    }

    @Override
    public boolean isUpgradeSupported() {
        if(http2Delegate != null) {
            return false;
        }
        return true;
    }

    @Override
    public void addCloseListener(ChannelListener<ClientConnection> listener) {
        closeListeners.add(listener);
    }

    @Override
    public void sendRequest(final ClientRequest request, final ClientCallback<ClientExchange> clientCallback) {
        if(http2Delegate != null) {
            http2Delegate.sendRequest(request, clientCallback);
            return;
        }
        count++;
        if (anyAreSet(state, UPGRADE_REQUESTED | UPGRADED | CLOSE_REQ | CLOSED)) {
            clientCallback.failed(UndertowClientMessages.MESSAGES.invalidConnectionState());
            return;
        }
        final HttpClientExchange httpClientExchange = new HttpClientExchange(clientCallback, request, this);
        boolean ssl = this.connection instanceof SslConnection;
        if(!ssl && !http2Tried && options.get(UndertowOptions.ENABLE_HTTP2, false) && !request.getRequestHeaders().contains(Headers.UPGRADE) && request.getMethod().equals(Methods.GET)) {
            //this is the first request, as we want to try a HTTP2 upgrade
            request.getRequestHeaders().put(new HttpString("HTTP2-Settings"), Http2ClearClientProvider.createSettingsFrame(options, bufferPool));
            request.getRequestHeaders().put(Headers.UPGRADE, Http2Channel.CLEARTEXT_UPGRADE_STRING);
            request.getRequestHeaders().put(Headers.CONNECTION, "Upgrade, HTTP2-Settings");
            http2Tried = true;
        }

        if (currentRequest == null) {
            initiateRequest(httpClientExchange);
        } else {
            pendingQueue.add(httpClientExchange);
        }
    }

    private void initiateRequest(HttpClientExchange httpClientExchange) {
        this.requestCount++;
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
        if(request.getMethod().equals(Methods.CONNECT)) {
            //we treat CONNECT like upgrade requests
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

        httpClientExchange.invokeReadReadyCallback();
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
        connection.getSinkChannel().setConduit(originalSinkConduit);
        connection.getSourceChannel().setConduit(pushBackStreamSourceConduit);
        return connection;
    }

    public void close() throws IOException {
        if(http2Delegate != null) {
            http2Delegate.close();
        }
        if (anyAreSet(state, CLOSED)) {
            return;
        }
        state |= CLOSED | CLOSE_REQ;
        ConnectionUtils.cleanClose(connection);
    }

    /**
     * Notification that the current request is finished
     */
    public void exchangeDone() {

        connection.getSinkChannel().setConduit(originalSinkConduit);
        connection.getSourceChannel().setConduit(pushBackStreamSourceConduit);
        connection.getSinkChannel().suspendWrites();
        connection.getSinkChannel().setWriteListener(null);

        if (anyAreSet(state, CLOSE_REQ)) {
            currentRequest = null;
            this.state |= CLOSED;
            safeClose(connection);
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

    public void requestDataSent() {
        if(http2UpgradeReceived) {
            doHttp2Upgrade();
        }
    }

    class ClientReadListener implements ChannelListener<StreamSourceChannel> {

        public void handleEvent(StreamSourceChannel channel) {

            HttpResponseBuilder builder = pendingResponse;
            final PooledByteBuffer pooled = bufferPool.allocate();
            final ByteBuffer buffer = pooled.getBuffer();
            boolean free = true;

            try {

                if (builder == null) {
                    //read ready when no request pending
                    buffer.clear();
                    try {
                        int res = channel.read(buffer);
                         if(res == -1) {
                            UndertowLogger.CLIENT_LOGGER.debugf("Connection to %s was closed by the target server", connection.getPeerAddress());
                            safeClose(HttpClientConnection.this);
                        } else if(res != 0) {
                             UndertowLogger.CLIENT_LOGGER.debugf("Target server %s sent unexpected data when no request pending, closing connection", connection.getPeerAddress());
                             safeClose(HttpClientConnection.this);
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
                        safeClose(HttpClientConnection.this);
                        // Cancel the current active request
                        currentRequest.setFailed(new IOException(MESSAGES.connectionClosed()));
                        return;
                    }

                    buffer.flip();

                    HttpResponseParser.INSTANCE.handle(buffer, state, builder);
                    if (buffer.hasRemaining()) {
                        free = false;
                        pushBackStreamSourceConduit.pushBack(new PooledAdaptor(pooled));
                    }

                } while (!state.isComplete());

                final ClientResponse response = builder.build();

                String connectionString = response.getResponseHeaders().getFirst(CONNECTION);

                //check if an upgrade worked
                if (anyAreSet(HttpClientConnection.this.state, UPGRADE_REQUESTED)) {
                    if ((connectionString == null || !UPGRADE.equalToString(connectionString)) && !response.getResponseHeaders().contains(UPGRADE)) {
                        if(!currentRequest.getRequest().getMethod().equals(Methods.CONNECT) || response.getResponseCode() != 200) { //make sure it was not actually a connect request
                            //just unset the upgrade requested flag
                            HttpClientConnection.this.state &= ~UPGRADE_REQUESTED;
                        }
                    }
                }

                if(connectionString != null) {
                    if (HttpString.tryFromString(connectionString).equals(Headers.CLOSE)) {
                        HttpClientConnection.this.state |= CLOSE_REQ;
                        //we are going to close, kill any queued connections
                        HttpClientExchange ex = pendingQueue.poll();
                        while (ex != null) {
                            ex.setFailed(new IOException(UndertowClientMessages.MESSAGES.connectionClosed()));
                            ex = pendingQueue.poll();
                        }
                    }
                }
                if(response.getResponseCode() == StatusCodes.SWITCHING_PROTOCOLS && Http2Channel.CLEARTEXT_UPGRADE_STRING.equals(response.getResponseHeaders().getFirst(Headers.UPGRADE))) {
                    //http2 upgrade

                    http2UpgradeReceived = true;
                    if(currentRequest.isRequestDataSent()) {
                        doHttp2Upgrade();
                    }
                } else if (builder.getStatusCode() == StatusCodes.CONTINUE) {
                    pendingResponse = new HttpResponseBuilder();
                    currentRequest.setContinueResponse(response);
                } else {
                    prepareResponseChannel(response, currentRequest);
                    channel.getReadSetter().set(null);
                    channel.suspendReads();
                    pendingResponse = null;
                    currentRequest.setResponse(response);
                    if(response.getResponseCode() == StatusCodes.EXPECTATION_FAILED) {
                        if(HttpContinue.requiresContinueResponse(currentRequest.getRequest().getRequestHeaders())) {
                            HttpClientConnection.this.state |= CLOSE_REQ;
                            ConduitStreamSinkChannel sinkChannel = HttpClientConnection.this.connection.getSinkChannel();
                            sinkChannel.shutdownWrites();
                            if(!sinkChannel.flush()) {
                                sinkChannel.setWriteListener(ChannelListeners.flushingChannelListener(null, null));
                                sinkChannel.resumeWrites();
                            }
                            currentRequest.terminateRequest();
                        }
                    }
                }


            } catch (Exception e) {
                UndertowLogger.CLIENT_LOGGER.exceptionProcessingRequest(e);
                safeClose(connection);
                currentRequest.setFailed(new IOException(e));
            } finally {
                if (free) {
                    pooled.close();
                    pooledBuffer = null;
                } else {
                    pooledBuffer = pooled;
                }
            }


        }
    }

    protected void doHttp2Upgrade() {
        try {
            StreamConnection connectedStreamChannel = this.performUpgrade();
            Http2Channel http2Channel = new Http2Channel(connectedStreamChannel, null, bufferPool, null, true, true, options);
            Http2ClientConnection http2ClientConnection = new Http2ClientConnection(http2Channel, currentRequest.getResponseCallback(), currentRequest.getRequest(), currentRequest.getRequest().getRequestHeaders().getFirst(Headers.HOST), clientStatistics, false);
            http2ClientConnection.getCloseSetter().set(new ChannelListener<ClientConnection>() {
                @Override
                public void handleEvent(ClientConnection channel) {
                    ChannelListeners.invokeChannelListener(HttpClientConnection.this, HttpClientConnection.this.closeSetter.get());
                }
            });
            http2Delegate = http2ClientConnection;
            connectedStreamChannel.getSourceChannel().wakeupReads(); //make sure the read listener is immediately invoked, as it may not happen if data is pushed back
            currentRequest = null;
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            safeClose(this);
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
        } else if (response.getProtocol().equals(Protocols.HTTP_1_1) && !Connectors.isEntityBodyAllowed(response.getResponseCode())) {
            connection.getSourceChannel().setConduit(new FixedLengthStreamSourceConduit(connection.getSourceChannel().getConduit(), 0, responseFinishedListener));
        } else {
            state |= CLOSE_REQ;
        }
    }

    private class ClientStatisticsImpl implements ClientStatistics {

        @Override
        public long getRequests() {
            return requestCount;
        }

        @Override
        public long getRead() {
            return read;
        }

        @Override
        public long getWritten() {
            return written;
        }

        @Override
        public void reset() {
            read = 0;
            written = 0;
            requestCount = 0;
        }
    }
}
