/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package io.undertow.client;

import io.undertow.UndertowLogger;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.IoUtils.safeClose;

/**
 * @author Emanuel Muckenhuber
 */
class HttpClientConnectionImpl extends HttpClientConnection implements ConnectedChannel {

    private final OptionMap options;
    private final ConnectedStreamChannel underlyingChannel;
    private final PushBackStreamChannel readChannel;

    private final Pool<ByteBuffer> bufferPool;
    private final HttpRequestQueue queuingStrategy;
    private final ClientReadListener readListener = new ClientReadListener();
    private final ChannelListener.Setter<ConnectedChannel> closeSetter;

    private static final int CLOSE_REQ = 1 << 30;
    private static final int CLOSED = 1 << 31;

    private volatile int state;
    private static final AtomicIntegerFieldUpdater<HttpClientConnectionImpl> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(HttpClientConnectionImpl.class, "state");
    private volatile boolean pipelining;

    HttpClientConnectionImpl(final ConnectedStreamChannel underlyingChannel, final PushBackStreamChannel readChannel, final OptionMap options, final HttpClientImpl client) {
        super(client);
        this.options = options;
        this.underlyingChannel = underlyingChannel;
        this.readChannel = readChannel;
        this.bufferPool = client.getBufferPool();
        //
        queuingStrategy = HttpRequestQueue.create(this, options);
        pipelining = queuingStrategy.supportsPipelining(); // TODO wait for the first response to determine this
        closeSetter = ChannelListeners.<ConnectedChannel>getDelegatingSetter(underlyingChannel.getCloseSetter(), this);

        getCloseSetter().set(new ChannelListener<ConnectedChannel>() {
            @Override
            public void handleEvent(ConnectedChannel channel) {
                IoUtils.safeClose(HttpClientConnectionImpl.this);
                client.connectionClosed(HttpClientConnectionImpl.this);
            }
        });
    }

    ConnectedStreamChannel getChannel() {
        return underlyingChannel;
    }

    Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

    @Override
    public SocketAddress getPeerAddress() {
        return underlyingChannel.getPeerAddress();
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        return underlyingChannel.getPeerAddress(type);
    }

    @Override
    public ChannelListener.Setter<? extends ConnectedChannel> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return underlyingChannel.getLocalAddress();
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        return underlyingChannel.getLocalAddress(type);
    }

    @Override
    public XnioWorker getWorker() {
        return underlyingChannel.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return underlyingChannel.getIoThread();
    }

    @Override
    public boolean isOpen() {
        return underlyingChannel.isOpen();
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return underlyingChannel.supportsOption(option);
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return underlyingChannel.getOption(option);
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return underlyingChannel.setOption(option, value);
    }

    @Override
    OptionMap getOptions() {
        return options;
    }

    @Override
    public HttpClientRequest sendRequest(String method, URI target) {
        return sendRequest(method, target, pipelining);
    }

    protected HttpClientRequest sendRequest(final String method, final URI target, final boolean pipelining) {
        return new HttpClientRequestImpl(this, underlyingChannel, method, target, pipelining);
    }

    @Override
    public IoFuture<ConnectedStreamChannel> upgradeToWebSocket(String service, OptionMap optionMap) {
        final FutureResult<ConnectedStreamChannel> result = new FutureResult<ConnectedStreamChannel>();
        try {
            // Upgrade the connection
            final HttpClientRequest request = sendRequest(Methods.GET_STRING, new URI("/"), false); // disable pipelining for connection upgrades
            request.getRequestHeaders().add(Headers.CONNECTION, Headers.UPGRADE_STRING);
            request.getRequestHeaders().add(Headers.UPGRADE, service);

            // Get the response
            final IoFuture<HttpClientResponse> responseFuture = request.writeRequest();
            responseFuture.addNotifier(new IoFuture.HandlingNotifier<HttpClientResponse, Object>() {
                @Override
                public void handleCancelled(Object attachment) {
                    result.setCancelled();
                }

                @Override
                public void handleFailed(IOException exception, Object attachment) {
                    result.setException(exception);
                }

                @Override
                public void handleDone(HttpClientResponse response, Object attachment) {
                    if(response.getResponseCode() == 101) {
                        // final AssembledConnectedStreamChannel channel = new AssembledConnectedStreamChannel(sourceChannel, underlyingChannel);
                        result.setResult(null); // TODO assemble channel
                    } else {
                        result.setException(new IOException());
                    }
                }
            }, null);
        } catch (IOException e) {
            result.setException(e);
        } catch (Exception e){
            result.setException(new IOException(e));
        }
        return result.getIoFuture();
    }

    @Override
    public void close() throws IOException {
        int oldState, newState;
        do {
            oldState = state;
            if(allAreSet(oldState, CLOSED)) {
                return;
            }
            newState = oldState | CLOSED | CLOSE_REQ;
        } while (! stateUpdater.compareAndSet(this, oldState, newState));
        underlyingChannel.close();
    }

    /**
     * Add a new request to the queue.
     *
     * @param request the request to addNewRequest
     * @throws IOException
     */
    void enqueueRequest(final PendingHttpRequest request) throws IOException {
        int oldState, newState;
        do {
            oldState = state;
            if(anyAreSet(oldState, CLOSE_REQ | CLOSED)) {
                throw new IOException();
            }
            newState = oldState + 1;
        } while (!stateUpdater.compareAndSet(this, oldState, newState));
        UndertowLogger.CLIENT_LOGGER.tracef("adding new request %s %s", request, request.getRequest());
        queuingStrategy.addNewRequest(request);
    }

    /**
     * Notification that sending the request has completed.
     *
     * @param request the request
     */
    void sendingCompleted(final PendingHttpRequest request) {
        queuingStrategy.requestSent(request);
        UndertowLogger.CLIENT_LOGGER.tracef("request fully sent %s", request);
        int currentState = state;
        if (allAreSet(currentState, CLOSE_REQ)) {
            try {
                underlyingChannel.shutdownWrites();
            } catch (IOException e) {
                UndertowLogger.CLIENT_LOGGER.debugf(e, "failed to shutdown writes");
            }
        }
    }

    /**
     * Notification that the request has completed.
     *
     * @param request the request
     */
    void requestCompleted(final PendingHttpRequest request) {
        int currentState = stateUpdater.getAndDecrement(this);
        queuingStrategy.requestCompleted(request);
        UndertowLogger.CLIENT_LOGGER.tracef("request completed %s", request);
        if (allAreSet(currentState, CLOSE_REQ)) {
            try {
                close();
            } catch (IOException e) {
                UndertowLogger.CLIENT_LOGGER.debugf(e, "failed to close channel");
            }
        }
    }

    /**
     * Request a close after the next request completed.
     *
     * @throws IOException
     */
    void requestConnectionClose() throws IOException {
        int oldState, newState;
        do {
            oldState = state;
            if(anyAreSet(oldState, CLOSE_REQ | CLOSED)) {
                return;
            }
            newState = oldState | CLOSE_REQ;
        } while (! stateUpdater.compareAndSet(this, oldState, newState));
        UndertowLogger.CLIENT_LOGGER.tracef("request to close the connection");
        if(newState == CLOSE_REQ) {
            close();
        }
    }

    void sendRequest(final PendingHttpRequest request, boolean fromCallback) {
        int currentState = state;
        if(anyAreSet(currentState, CLOSE_REQ | CLOSED)) {
            request.setCancelled();
            sendingCompleted(request);
            return;
        }
        UndertowLogger.CLIENT_LOGGER.tracef("start sending request %s", request);
        if(! fromCallback) {
            request.sendRequest();
        } else {
            underlyingChannel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                @Override
                public void handleEvent(StreamSinkChannel channel) {
                    request.sendRequest();
                }
            });
            underlyingChannel.resumeWrites();
        }
    }

    void readResponse(PendingHttpRequest request) {
        assert readListener.activeRequest == null;
        UndertowLogger.CLIENT_LOGGER.tracef("start reading response for %s", request);
        readListener.activeRequest = request;
        readChannel.getReadSetter().set(readListener);
        readChannel.resumeReads();
    }

    class ClientReadListener implements ChannelListener<PushBackStreamChannel> {

        volatile PendingHttpRequest activeRequest;

        @Override
        public void handleEvent(PushBackStreamChannel channel) {

            final PendingHttpRequest builder = activeRequest;
            final Pooled<ByteBuffer> pooled = bufferPool.allocate();
            final ByteBuffer buffer = pooled.getResource();
            boolean free = true;

            try {
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
                        return;
                    }

                    if(res == 0) {
                        if (!channel.isReadResumed()) {
                            channel.getReadSetter().set(this);
                            channel.resumeReads();
                        }
                        return;
                    } else if (res == -1) {
                        try {
                            channel.suspendReads();
                            channel.shutdownReads();
                            final StreamSinkChannel requestChannel = underlyingChannel;
                            requestChannel.shutdownWrites();
                            // will return false if there's a response queued ahead of this one, so we'll set up a listener then
                            if (!requestChannel.flush()) {
                                requestChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, null));
                                requestChannel.resumeWrites();
                            }
                            // Cancel the current active request
                            builder.setFailed(new IOException("response channel closed"));
                        } catch (IOException e) {
                            if (UndertowLogger.CLIENT_LOGGER.isDebugEnabled()) {
                                UndertowLogger.CLIENT_LOGGER.debugf(e, "Connection closed with IOException when attempting to shut down reads");
                            }
                            // Cancel the current active request
                            builder.setFailed(e);
                            IoUtils.safeClose(channel);
                            return;
                        }
                        return;
                    }

                    buffer.flip();

                    int remaining = HttpResponseParser.INSTANCE.handle(buffer, res, state, builder);
                    if (remaining > 0) {
                        free = false;
                        channel.unget(pooled);
                    }

                } while(! state.isComplete());

                channel.getReadSetter().set(null);
                channel.suspendReads();
                activeRequest = null;

                // Process the complete response
                builder.handleResponseComplete(HttpClientConnectionImpl.this, channel);

            } catch (Exception e) {
                UndertowLogger.CLIENT_LOGGER.exceptionProcessingRequest(e);
                IoUtils.safeClose(underlyingChannel);
            } finally {
                if (free) pooled.free();
            }
        }

    }

}
