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

package io.undertow.client.http2;

import static io.undertow.protocols.http2.Http2Channel.AUTHORITY;
import static io.undertow.protocols.http2.Http2Channel.METHOD;
import static io.undertow.protocols.http2.Http2Channel.PATH;
import static io.undertow.protocols.http2.Http2Channel.SCHEME;
import static io.undertow.protocols.http2.Http2Channel.STATUS;
import static io.undertow.util.Headers.CONTENT_LENGTH;
import static io.undertow.util.Headers.TRANSFER_ENCODING;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.undertow.client.ClientStatistics;
import io.undertow.protocols.http2.Http2GoAwayStreamSourceChannel;
import io.undertow.protocols.http2.Http2PushPromiseStreamSourceChannel;
import io.undertow.util.HeaderValues;
import io.undertow.util.Methods;
import io.undertow.util.NetworkUtils;
import io.undertow.util.Protocols;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
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
import io.undertow.client.ProxiedRequestAttachments;
import io.undertow.protocols.http2.AbstractHttp2StreamSourceChannel;
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.protocols.http2.Http2HeadersStreamSinkChannel;
import io.undertow.protocols.http2.Http2PingStreamSourceChannel;
import io.undertow.protocols.http2.Http2RstStreamStreamSourceChannel;
import io.undertow.protocols.http2.Http2StreamSourceChannel;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
public class Http2ClientConnection implements ClientConnection {

    private final Http2Channel http2Channel;
    private final ChannelListener.SimpleSetter<ClientConnection> closeSetter = new ChannelListener.SimpleSetter<>();

    private final Map<Integer, Http2ClientExchange> currentExchanges = new ConcurrentHashMap<>();

    private boolean initialUpgradeRequest;
    private final String defaultHost;
    private final ClientStatistics clientStatistics;
    private final List<ChannelListener<ClientConnection>> closeListeners = new CopyOnWriteArrayList<>();
    private final boolean secure;

    public Http2ClientConnection(Http2Channel http2Channel, boolean initialUpgradeRequest, String defaultHost, ClientStatistics clientStatistics, boolean secure) {

        this.http2Channel = http2Channel;
        this.defaultHost = defaultHost;
        this.clientStatistics = clientStatistics;
        this.secure = secure;
        http2Channel.getReceiveSetter().set(new Http2ReceiveListener());
        http2Channel.resumeReceives();
        http2Channel.addCloseTask(new ChannelListener<Http2Channel>() {
            @Override
            public void handleEvent(Http2Channel channel) {
                ChannelListeners.invokeChannelListener(Http2ClientConnection.this, closeSetter.get());
                for(ChannelListener<ClientConnection> listener : closeListeners) {
                    listener.handleEvent(Http2ClientConnection.this);
                }
            }
        });
        this.initialUpgradeRequest = initialUpgradeRequest;
    }

    public Http2ClientConnection(Http2Channel http2Channel, ClientCallback<ClientExchange> upgradeReadyCallback, ClientRequest clientRequest, String defaultHost, ClientStatistics clientStatistics, boolean secure) {

        this.http2Channel = http2Channel;
        this.defaultHost = defaultHost;
        this.clientStatistics = clientStatistics;
        this.secure = secure;
        http2Channel.getReceiveSetter().set(new Http2ReceiveListener());
        http2Channel.resumeReceives();
        http2Channel.addCloseTask(new ChannelListener<Http2Channel>() {
            @Override
            public void handleEvent(Http2Channel channel) {
                ChannelListeners.invokeChannelListener(Http2ClientConnection.this, closeSetter.get());
            }
        });
        this.initialUpgradeRequest = false;

        Http2ClientExchange exchange = new Http2ClientExchange(this, null, clientRequest);
        exchange.setResponseListener(upgradeReadyCallback);
        currentExchanges.put(1, exchange);
    }

    @Override
    public synchronized void sendRequest(ClientRequest request, ClientCallback<ClientExchange> clientCallback) {
        request.getRequestHeaders().put(METHOD, request.getMethod().toString());
        boolean connectRequest = request.getMethod().equals(Methods.CONNECT);
        if(!connectRequest) {
            request.getRequestHeaders().put(PATH, request.getPath());
            request.getRequestHeaders().put(SCHEME, secure ? "https" : "http");
        }
        final String host = request.getRequestHeaders().getFirst(Headers.HOST);
        if(host != null) {
            request.getRequestHeaders().put(AUTHORITY, host);
        } else {
            request.getRequestHeaders().put(AUTHORITY, defaultHost);
        }
        request.getRequestHeaders().remove(Headers.HOST);


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
        } else if (transferEncodingString == null && !connectRequest) {
            hasContent = false;
        }

        request.getRequestHeaders().remove(Headers.CONNECTION);
        request.getRequestHeaders().remove(Headers.KEEP_ALIVE);
        request.getRequestHeaders().remove(Headers.TRANSFER_ENCODING);

        //setup the X-Forwarded-* headers
        String peer = request.getAttachment(ProxiedRequestAttachments.REMOTE_HOST);
        if(peer != null) {
            request.getRequestHeaders().put(Headers.X_FORWARDED_FOR, peer);
        }
        Boolean proto = request.getAttachment(ProxiedRequestAttachments.IS_SSL);
        if(proto == null || !proto) {
            request.getRequestHeaders().put(Headers.X_FORWARDED_PROTO, "http");
        } else {
            request.getRequestHeaders().put(Headers.X_FORWARDED_PROTO, "https");
        }
        String hn = request.getAttachment(ProxiedRequestAttachments.SERVER_NAME);
        if(hn != null) {
            request.getRequestHeaders().put(Headers.X_FORWARDED_HOST, NetworkUtils.formatPossibleIpv6Address(hn));
        }
        Integer port = request.getAttachment(ProxiedRequestAttachments.SERVER_PORT);
        if(port != null) {
            request.getRequestHeaders().put(Headers.X_FORWARDED_PORT, port);
        }


        Http2HeadersStreamSinkChannel sinkChannel;
        try {
            sinkChannel = http2Channel.createStream(request.getRequestHeaders());
        } catch (IOException e) {
            clientCallback.failed(e);
            return;
        }
        Http2ClientExchange exchange = new Http2ClientExchange(this, sinkChannel, request);
        currentExchanges.put(sinkChannel.getStreamId(), exchange);


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
        IoUtils.safeClose(Http2ClientConnection.this);
        for (Map.Entry<Integer, Http2ClientExchange> entry : currentExchanges.entrySet()) {
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
        return http2Channel.getBufferPool();
    }

    @Override
    public SocketAddress getPeerAddress() {
        return http2Channel.getPeerAddress();
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        return http2Channel.getPeerAddress(type);
    }

    @Override
    public ChannelListener.Setter<? extends ClientConnection> getCloseSetter() {
        return closeSetter;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return http2Channel.getLocalAddress();
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        return http2Channel.getLocalAddress(type);
    }

    @Override
    public XnioWorker getWorker() {
        return http2Channel.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return http2Channel.getIoThread();
    }

    @Override
    public boolean isOpen() {
        return http2Channel.isOpen() && !http2Channel.isPeerGoneAway() && !http2Channel.isThisGoneAway();
    }

    @Override
    public void close() throws IOException {
        try {
            http2Channel.sendGoAway(0);
        } finally {
            for(Map.Entry<Integer, Http2ClientExchange> entry : currentExchanges.entrySet()) {
                entry.getValue().failed(new ClosedChannelException());
            }
            currentExchanges.clear();
        }
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

    @Override
    public void addCloseListener(ChannelListener<ClientConnection> listener) {
        closeListeners.add(listener);
    }

    private class Http2ReceiveListener implements ChannelListener<Http2Channel> {

        @Override
        public void handleEvent(Http2Channel channel) {
            try {
                AbstractHttp2StreamSourceChannel result = channel.receive();
                if (result instanceof Http2StreamSourceChannel) {
                    final Http2StreamSourceChannel streamSourceChannel = (Http2StreamSourceChannel) result;

                    int statusCode = Integer.parseInt(streamSourceChannel.getHeaders().getFirst(STATUS));
                    Http2ClientExchange request = currentExchanges.get(streamSourceChannel.getStreamId());
                    if(statusCode < 200) {
                        //this is an informational response 1xx response
                        if(statusCode == 100) {
                            //a continue response
                            request.setContinueResponse(request.createResponse(streamSourceChannel));
                        }
                        Channels.drain(result, Long.MAX_VALUE);
                        return;
                    }

                    result.addCloseTask(new ChannelListener<AbstractHttp2StreamSourceChannel>() {
                        @Override
                        public void handleEvent(AbstractHttp2StreamSourceChannel channel) {
                            currentExchanges.remove(streamSourceChannel.getStreamId());
                        }
                    });
                    streamSourceChannel.setCompletionListener(new ChannelListener<Http2StreamSourceChannel>() {
                        @Override
                        public void handleEvent(Http2StreamSourceChannel channel) {
                            currentExchanges.remove(streamSourceChannel.getStreamId());
                        }
                    });
                    if (request == null && initialUpgradeRequest) {
                        Channels.drain(result, Long.MAX_VALUE);
                        initialUpgradeRequest = false;
                        return;
                    } else if(request == null) {
                        channel.sendGoAway(Http2Channel.ERROR_PROTOCOL_ERROR);
                        IoUtils.safeClose(Http2ClientConnection.this);
                        return;
                    }
                    request.responseReady(streamSourceChannel);
                } else if (result instanceof Http2PingStreamSourceChannel) {
                    handlePing((Http2PingStreamSourceChannel) result);
                } else if (result instanceof Http2RstStreamStreamSourceChannel) {
                    Http2RstStreamStreamSourceChannel rstStream = (Http2RstStreamStreamSourceChannel) result;
                    int stream = rstStream.getStreamId();
                    UndertowLogger.REQUEST_LOGGER.debugf("Client received RST_STREAM for stream %s", stream);
                    Http2ClientExchange exchange = currentExchanges.get(stream);

                    if(exchange != null) {
                        //if we have not yet received a response we treat this as an error
                        exchange.failed(UndertowMessages.MESSAGES.http2StreamWasReset());
                    }
                    Channels.drain(result, Long.MAX_VALUE);
                } else if (result instanceof Http2PushPromiseStreamSourceChannel) {
                    Http2PushPromiseStreamSourceChannel stream = (Http2PushPromiseStreamSourceChannel) result;
                    Http2ClientExchange request = currentExchanges.get(stream.getAssociatedStreamId());
                    if(request == null) {
                        channel.sendGoAway(Http2Channel.ERROR_PROTOCOL_ERROR); //according to the spec this is a connection error
                    } else if(request.getPushCallback() == null) {
                        channel.sendRstStream(stream.getPushedStreamId(), Http2Channel.ERROR_REFUSED_STREAM);
                    } else {
                        ClientRequest cr = new ClientRequest();
                        cr.setMethod(new HttpString(stream.getHeaders().getFirst(METHOD)));
                        cr.setPath(stream.getHeaders().getFirst(PATH));
                        cr.setProtocol(Protocols.HTTP_1_1);
                        for (HeaderValues header : stream.getHeaders()) {
                            cr.getRequestHeaders().putAll(header.getHeaderName(), header);
                        }

                        Http2ClientExchange newExchange = new Http2ClientExchange(Http2ClientConnection.this, null, cr);

                        if(!request.getPushCallback().handlePush(request, newExchange)) {
                            channel.sendRstStream(stream.getPushedStreamId(), Http2Channel.ERROR_REFUSED_STREAM);
                            IoUtils.safeClose(stream);
                        } else {
                            currentExchanges.put(stream.getPushedStreamId(), newExchange);
                        }
                    }
                    Channels.drain(result, Long.MAX_VALUE);

                } else if (result instanceof Http2GoAwayStreamSourceChannel) {
                    close();
                } else if(!channel.isOpen()) {
                    throw UndertowMessages.MESSAGES.channelIsClosed();
                } else if(result != null) {
                    Channels.drain(result, Long.MAX_VALUE);
                }

            } catch (IOException e) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(Http2ClientConnection.this);
                for (Map.Entry<Integer, Http2ClientExchange> entry : currentExchanges.entrySet()) {
                    try {
                        entry.getValue().failed(e);
                    } catch (Exception ex) {
                        UndertowLogger.REQUEST_IO_LOGGER.ioException(new IOException(ex));
                    }
                }
            }

        }

        private void handlePing(Http2PingStreamSourceChannel frame) {
            byte[] id = frame.getData();
            if (!frame.isAck()) {
                //server side ping, return it
                frame.getHttp2Channel().sendPing(id);
            }
        }

    }
}
