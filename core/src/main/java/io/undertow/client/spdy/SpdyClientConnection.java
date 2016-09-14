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

package io.undertow.client.spdy;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientStatistics;
import io.undertow.protocols.spdy.SpdyChannel;
import io.undertow.protocols.spdy.SpdyPingStreamSourceChannel;
import io.undertow.protocols.spdy.SpdyRstStreamStreamSourceChannel;
import io.undertow.protocols.spdy.SpdyStreamSourceChannel;
import io.undertow.protocols.spdy.SpdySynReplyStreamSourceChannel;
import io.undertow.protocols.spdy.SpdySynStreamStreamSinkChannel;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import io.undertow.connector.ByteBufferPool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.undertow.util.Headers.CONTENT_LENGTH;
import static io.undertow.util.Headers.TRANSFER_ENCODING;

/**
 * @author Stuart Douglas
 */
public class SpdyClientConnection implements ClientConnection {


    static final HttpString METHOD = new HttpString(":method");
    static final HttpString PATH = new HttpString(":path");
    static final HttpString SCHEME = new HttpString(":scheme");
    static final HttpString VERSION = new HttpString(":version");
    static final HttpString HOST = new HttpString(":host");
    static final HttpString STATUS = new HttpString(":status");

    private final SpdyChannel spdyChannel;
    private final ChannelListener.SimpleSetter<ClientConnection> closeSetter = new ChannelListener.SimpleSetter<>();

    private final Map<Integer, SpdyClientExchange> currentExchanges = new ConcurrentHashMap<>();

    private final ClientStatistics clientStatistics;
    public SpdyClientConnection(SpdyChannel spdyChannel, ClientStatistics clientStatistics) {
        this.spdyChannel = spdyChannel;
        this.clientStatistics = clientStatistics;
        spdyChannel.getReceiveSetter().set(new SpdyReceiveListener());
        spdyChannel.resumeReceives();
        spdyChannel.addCloseTask(new ChannelListener<SpdyChannel>() {
            @Override
            public void handleEvent(SpdyChannel channel) {
                ChannelListeners.invokeChannelListener(SpdyClientConnection.this, closeSetter.get());
            }
        });
    }

    @Override
    public synchronized void sendRequest(ClientRequest request, ClientCallback<ClientExchange> clientCallback) {
        request.getRequestHeaders().put(PATH, request.getPath());
        request.getRequestHeaders().put(SCHEME, "https");
        request.getRequestHeaders().put(VERSION, request.getProtocol().toString());
        request.getRequestHeaders().put(METHOD, request.getMethod().toString());
        request.getRequestHeaders().put(HOST, request.getRequestHeaders().getFirst(Headers.HOST));
        request.getRequestHeaders().remove(Headers.HOST);

        SpdySynStreamStreamSinkChannel sinkChannel;
        try {
            sinkChannel = spdyChannel.createStream(request.getRequestHeaders());
        } catch (IOException e) {
            clientCallback.failed(e);
            return;
        }
        SpdyClientExchange exchange = new SpdyClientExchange(this, sinkChannel, request);
        currentExchanges.put(sinkChannel.getStreamId(), exchange);


        boolean hasContent = true;

        String fixedLengthString = request.getRequestHeaders().getFirst(CONTENT_LENGTH);
        String transferEncodingString = request.getRequestHeaders().getLast(TRANSFER_ENCODING);
        if (fixedLengthString != null) {
            try {
                long length = Long.parseLong(fixedLengthString);
                hasContent = length != 0;
            } catch (NumberFormatException e) {
                handleError(new IOException(e));
                return;
            }
        } else if (transferEncodingString == null) {
            hasContent = false;
        }
        if(clientCallback != null) {
            clientCallback.completed(exchange);
        }
        if (!hasContent) {
            //if there is no content we flush the response channel.
            //otherwise it is up to the user
            try {
                sinkChannel.shutdownWrites();
                if (!sinkChannel.flush()) {
                    sinkChannel.getWriteSetter().set(ChannelListeners.flushingChannelListener(null, new ChannelExceptionHandler<StreamSinkChannel>() {
                        @Override
                        public void handleException(StreamSinkChannel channel, IOException exception) {
                            handleError(exception);
                        }
                    }));
                    sinkChannel.resumeWrites();
                }
            } catch (IOException e) {
                handleError(e);
            }
        } else if (!sinkChannel.isWriteResumed()) {
            try {
                //TODO: this needs some more thought
                if (!sinkChannel.flush()) {
                    sinkChannel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        @Override
                        public void handleEvent(StreamSinkChannel channel) {
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

    private void handleError(IOException e) {

        UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
        IoUtils.safeClose(SpdyClientConnection.this);
        for (Map.Entry<Integer, SpdyClientExchange> entry : currentExchanges.entrySet()) {
            try {
                entry.getValue().failed(e);
            } catch (Exception ex) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(new IOException(ex));
            }
        }
    }

    @Override
    public StreamConnection performUpgrade() throws IOException {
        throw UndertowMessages.MESSAGES.upgradeNotSupported();
    }

    @Override
    public ByteBufferPool getBufferPool() {
        return spdyChannel.getBufferPool();
    }

    @Override
    public SocketAddress getPeerAddress() {
        return spdyChannel.getPeerAddress();
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        return spdyChannel.getPeerAddress(type);
    }

    @Override
    public ChannelListener.Setter<? extends ClientConnection> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return spdyChannel.getLocalAddress();
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        return spdyChannel.getLocalAddress(type);
    }

    @Override
    public XnioWorker getWorker() {
        return spdyChannel.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return spdyChannel.getIoThread();
    }

    @Override
    public boolean isOpen() {
        return spdyChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        spdyChannel.sendGoAway(SpdyChannel.CLOSE_OK);
    }

    @Override
    public boolean supportsOption(Option<?> option) {
        return false;
    }

    @Override
    public <T> T getOption(Option<T> option) throws IOException {
        return null;
    }

    @Override
    public <T> T setOption(Option<T> option, T value) throws IllegalArgumentException, IOException {
        return null;
    }

    @Override
    public boolean isUpgraded() {
        return false;
    }

    @Override
    public boolean isPushSupported() {
        return true;
    }

    @Override
    public boolean isMultiplexingSupported() {
        return true;
    }

    @Override
    public ClientStatistics getStatistics() {
        return clientStatistics;
    }

    @Override
    public boolean isUpgradeSupported() {
        return false;
    }

    private class SpdyReceiveListener implements ChannelListener<SpdyChannel> {

        @Override
        public void handleEvent(SpdyChannel channel) {
            try {
                SpdyStreamSourceChannel result = channel.receive();
                if (result instanceof SpdySynReplyStreamSourceChannel) {
                    final int streamId = ((SpdySynReplyStreamSourceChannel) result).getStreamId();
                    SpdyClientExchange request = currentExchanges.get(streamId);
                    result.addCloseTask(new ChannelListener<SpdyStreamSourceChannel>() {
                        @Override
                        public void handleEvent(SpdyStreamSourceChannel channel) {
                            currentExchanges.remove(streamId);
                        }
                    });
                    if (request == null) {

                        //server side initiated stream, we can't deal with that at the moment
                        //just fail
                        //TODO: either handle this properly or at the very least send RST_STREAM
                        channel.sendGoAway(SpdyChannel.CLOSE_PROTOCOL_ERROR);
                        IoUtils.safeClose(SpdyClientConnection.this);
                        return;
                    }
                    request.responseReady((SpdySynReplyStreamSourceChannel) result);

                } else if (result instanceof SpdyPingStreamSourceChannel) {
                    handlePing((SpdyPingStreamSourceChannel) result);
                } else if (result instanceof SpdyRstStreamStreamSourceChannel) {
                    int stream = ((SpdyRstStreamStreamSourceChannel)result).getStreamId();
                    UndertowLogger.REQUEST_LOGGER.debugf("Client received RST_STREAM for stream %s", stream);
                    SpdyClientExchange exchange = currentExchanges.get(stream);
                    if(exchange != null) {
                        exchange.failed(UndertowMessages.MESSAGES.spdyStreamWasReset());
                    }
                } else if(!channel.isOpen()) {
                    throw UndertowMessages.MESSAGES.channelIsClosed();
                }

            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(SpdyClientConnection.this);
                for (Map.Entry<Integer, SpdyClientExchange> entry : currentExchanges.entrySet()) {
                    try {
                        entry.getValue().failed(e);
                    } catch (Exception ex) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(new IOException(ex));
                    }
                }
            }

        }

        private void handlePing(SpdyPingStreamSourceChannel frame) {
            int id = frame.getId();
            if (id % 2 == 0) {
                //server side ping, return it
                frame.getSpdyChannel().sendPing(id);
            }
        }

    }
}
