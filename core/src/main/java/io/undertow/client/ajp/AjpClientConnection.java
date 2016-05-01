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

package io.undertow.client.ajp;

import static io.undertow.util.Headers.CLOSE;
import static io.undertow.util.Headers.CONNECTION;
import static io.undertow.util.Headers.CONTENT_LENGTH;
import static io.undertow.util.Headers.TRANSFER_ENCODING;
import static io.undertow.util.Headers.UPGRADE;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.IoUtils.safeClose;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.undertow.client.ClientStatistics;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Option;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.Channels;
import org.xnio.channels.StreamSinkChannel;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.client.UndertowClientMessages;
import io.undertow.protocols.ajp.AbstractAjpClientStreamSourceChannel;
import io.undertow.protocols.ajp.AjpClientChannel;
import io.undertow.protocols.ajp.AjpClientRequestClientStreamSinkChannel;
import io.undertow.protocols.ajp.AjpClientResponseStreamSourceChannel;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.Protocols;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class AjpClientConnection extends AbstractAttachable implements Closeable, ClientConnection {

    public final ChannelListener<AjpClientRequestClientStreamSinkChannel> requestFinishListener = new ChannelListener<AjpClientRequestClientStreamSinkChannel>() {
        @Override
        public void handleEvent(AjpClientRequestClientStreamSinkChannel channel) {
            currentRequest.terminateRequest();
        }
    };
    public final ChannelListener<AjpClientResponseStreamSourceChannel> responseFinishedListener = new ChannelListener<AjpClientResponseStreamSourceChannel>() {
        @Override
        public void handleEvent(AjpClientResponseStreamSourceChannel channel) {
            currentRequest.terminateResponse();
        }
    };

    private final Deque<AjpClientExchange> pendingQueue = new ArrayDeque<>();
    private AjpClientExchange currentRequest;

    private final OptionMap options;
    private final AjpClientChannel connection;

    private final ByteBufferPool bufferPool;

    private static final int UPGRADED = 1 << 28;
    private static final int UPGRADE_REQUESTED = 1 << 29;
    private static final int CLOSE_REQ = 1 << 30;
    private static final int CLOSED = 1 << 31;

    private int state;

    private final ChannelListener.SimpleSetter<AjpClientConnection> closeSetter = new ChannelListener.SimpleSetter<>();
    private final ClientStatistics clientStatistics;
    private final List<ChannelListener<ClientConnection>> closeListeners = new CopyOnWriteArrayList<>();

    AjpClientConnection(final AjpClientChannel connection, final OptionMap options, final ByteBufferPool bufferPool, ClientStatistics clientStatistics) {
        this.clientStatistics = clientStatistics;
        this.options = options;
        this.connection = connection;
        this.bufferPool = bufferPool;

        connection.addCloseTask(new ChannelListener<AjpClientChannel>() {
            @Override
            public void handleEvent(AjpClientChannel channel) {
                ChannelListeners.invokeChannelListener(AjpClientConnection.this, closeSetter.get());
                for(ChannelListener<ClientConnection> listener : closeListeners) {
                    listener.handleEvent(AjpClientConnection.this);
                }
            }
        });
        connection.getReceiveSetter().set(new ClientReceiveListener());
        connection.resumeReceives();
    }

    @Override
    public ByteBufferPool getBufferPool() {
        return bufferPool;
    }


    @Override
    public SocketAddress getPeerAddress() {
        return connection.getPeerAddress();
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
    public boolean isPushSupported() {
        return false;
    }

    @Override
    public boolean isMultiplexingSupported() {
        return false;
    }

    @Override
    public ClientStatistics getStatistics() {
        return clientStatistics;
    }

    @Override
    public boolean isUpgradeSupported() {
        return false;
    }

    @Override
    public void addCloseListener(ChannelListener<ClientConnection> listener) {
        closeListeners.add(listener);
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

        long length = 0;
        String fixedLengthString = request.getRequestHeaders().getFirst(CONTENT_LENGTH);
        String transferEncodingString = request.getRequestHeaders().getLast(TRANSFER_ENCODING);

        if (fixedLengthString != null) {
            length = Long.parseLong(fixedLengthString);
        } else if (transferEncodingString != null) {
            length = -1;
        }

        AjpClientRequestClientStreamSinkChannel sinkChannel = connection.sendRequest(request.getMethod(), request.getPath(), request.getProtocol(), request.getRequestHeaders(), request, requestFinishListener);
        currentRequest.setRequestChannel(sinkChannel);

        AjpClientExchange.invokeReadReadyCallback(AjpClientExchange);
        if (length == 0) {
            //if there is no content we flush the response channel.
            //otherwise it is up to the user
            try {
                sinkChannel.shutdownWrites();
                if (!sinkChannel.flush()) {
                    handleFailedFlush(sinkChannel);
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private void handleFailedFlush(AjpClientRequestClientStreamSinkChannel sinkChannel) {
        sinkChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, new ChannelExceptionHandler<StreamSinkChannel>() {
            @Override
            public void handleException(StreamSinkChannel channel, IOException exception) {
                handleError(exception);
            }
        }));
        sinkChannel.resumeWrites();
    }

    private void handleError(IOException exception) {
        currentRequest.setFailed(exception);
        safeClose(connection);
    }

    public StreamConnection performUpgrade() throws IOException {
        throw UndertowMessages.MESSAGES.upgradeNotSupported();
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
        currentRequest = null;

        if (anyAreSet(state, CLOSE_REQ)) {
            safeClose(connection);
        } else if (anyAreSet(state, UPGRADE_REQUESTED)) {
            safeClose(connection); //we don't support upgrade, just close the connection to be safe
            return;
        }

        AjpClientExchange next = pendingQueue.poll();

        if (next != null) {
            initiateRequest(next);
        }
    }

    public void requestClose() {
        state |= CLOSE_REQ;
    }


    class ClientReceiveListener implements ChannelListener<AjpClientChannel> {

        public void handleEvent(AjpClientChannel channel) {
            try {
                AbstractAjpClientStreamSourceChannel result = channel.receive();
                if(result == null) {
                    return;
                }

                if(result instanceof AjpClientResponseStreamSourceChannel) {
                    AjpClientResponseStreamSourceChannel response = (AjpClientResponseStreamSourceChannel) result;
                    response.setFinishListener(responseFinishedListener);
                    ClientResponse cr = new ClientResponse(response.getStatusCode(), response.getReasonPhrase(), currentRequest.getRequest().getProtocol(), response.getHeaders());
                    if (response.getStatusCode() == 100) {
                        currentRequest.setContinueResponse(cr);
                    } else {
                        currentRequest.setResponseChannel(response);
                        currentRequest.setResponse(cr);
                    }
                } else {
                    //TODO: ping, pong ETC
                    Channels.drain(result, Long.MAX_VALUE);
                }

            } catch (Exception e) {
                UndertowLogger.CLIENT_LOGGER.exceptionProcessingRequest(e);
                safeClose(connection);
                if(currentRequest != null) {
                    currentRequest.setFailed(e instanceof IOException ? (IOException) e : new IOException(e));
                }
            }
        }
    }
}
