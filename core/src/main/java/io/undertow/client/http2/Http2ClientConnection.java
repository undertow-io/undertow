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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.undertow.client.ClientStatistics;
import io.undertow.protocols.http2.Http2DataStreamSinkChannel;
import io.undertow.protocols.http2.Http2GoAwayStreamSourceChannel;
import io.undertow.protocols.http2.Http2PushPromiseStreamSourceChannel;
import io.undertow.server.protocol.http.HttpAttachments;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Methods;
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

    private static final AtomicLong PING_COUNTER = new AtomicLong();


    private boolean initialUpgradeRequest;
    private final String defaultHost;
    private final ClientStatistics clientStatistics;
    private final List<ChannelListener<ClientConnection>> closeListeners = new CopyOnWriteArrayList<>();
    private final boolean secure;

    private final Map<PingKey, PingListener> outstandingPings = new HashMap<>();

    private final ChannelListener<Http2Channel> closeTask = new ChannelListener<Http2Channel>() {
        @Override
        public void handleEvent(Http2Channel channel) {
            ChannelListeners.invokeChannelListener(Http2ClientConnection.this, closeSetter.get());
            for (ChannelListener<ClientConnection> listener : closeListeners) {
                listener.handleEvent(Http2ClientConnection.this);
            }
            for (Map.Entry<Integer, Http2ClientExchange> entry : currentExchanges.entrySet()) {
                entry.getValue().failed(new ClosedChannelException());
            }
            currentExchanges.clear();
        }
    };

    public Http2ClientConnection(Http2Channel http2Channel, boolean initialUpgradeRequest, String defaultHost, ClientStatistics clientStatistics, boolean secure) {

        this.http2Channel = http2Channel;
        this.defaultHost = defaultHost;
        this.clientStatistics = clientStatistics;
        this.secure = secure;
        http2Channel.getReceiveSetter().set(new Http2ReceiveListener());
        http2Channel.resumeReceives();
        http2Channel.addCloseTask(closeTask);
        this.initialUpgradeRequest = initialUpgradeRequest;
    }

    public Http2ClientConnection(Http2Channel http2Channel, ClientCallback<ClientExchange> upgradeReadyCallback, ClientRequest clientRequest, String defaultHost, ClientStatistics clientStatistics, boolean secure) {

        this.http2Channel = http2Channel;
        this.defaultHost = defaultHost;
        this.clientStatistics = clientStatistics;
        this.secure = secure;
        http2Channel.getReceiveSetter().set(new Http2ReceiveListener());
        http2Channel.resumeReceives();
        http2Channel.addCloseTask(closeTask);
        this.initialUpgradeRequest = false;

        Http2ClientExchange exchange = new Http2ClientExchange(this, null, clientRequest);
        exchange.setResponseListener(upgradeReadyCallback);
        currentExchanges.put(1, exchange);
    }

    @Override
    public void sendRequest(ClientRequest request, ClientCallback<ClientExchange> clientCallback) {
        if(!http2Channel.isOpen()) {
            clientCallback.failed(new ClosedChannelException());
            return;
        }
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

        Http2HeadersStreamSinkChannel sinkChannel;
        try {
            sinkChannel = http2Channel.createStream(request.getRequestHeaders());
        } catch (Throwable t) {
            IOException e = t instanceof IOException ? (IOException) t : new IOException(t);
            clientCallback.failed(e);
            return;
        }
        Http2ClientExchange exchange = new Http2ClientExchange(this, sinkChannel, request);
        currentExchanges.put(sinkChannel.getStreamId(), exchange);

        sinkChannel.setTrailersProducer(new Http2DataStreamSinkChannel.TrailersProducer() {
            @Override
            public HeaderMap getTrailers() {
                HeaderMap attachment = exchange.getAttachment(HttpAttachments.RESPONSE_TRAILERS);
                Supplier<HeaderMap> supplier = exchange.getAttachment(HttpAttachments.RESPONSE_TRAILER_SUPPLIER);
                if(attachment != null && supplier == null) {
                    return attachment;
                } else if(attachment == null && supplier != null) {
                    return supplier.get();
                } else if(attachment != null) {
                    HeaderMap supplied = supplier.get();
                    for(HeaderValues k : supplied) {
                        attachment.putAll(k.getHeaderName(), k);
                    }
                    return attachment;
                } else {
                    return null;
                }
            }
        });

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
            } catch (Throwable e) {
                handleError(e);
            }
        }
    }

    private void handleError(Throwable t) {
        IOException e = t instanceof IOException ? (IOException) t : new IOException(t);
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

    @Override
    public boolean isPingSupported() {
        return true;
    }

    @Override
    public void sendPing(PingListener listener, long timeout, TimeUnit timeUnit) {
        long count = PING_COUNTER.incrementAndGet();
        byte[] data = new byte[8];
        data[0] = (byte) count;
        data[1] = (byte)(count << 8);
        data[2] = (byte)(count << 16);
        data[3] = (byte)(count << 24);
        data[4] = (byte)(count << 32);
        data[5] = (byte)(count << 40);
        data[6] = (byte)(count << 48);
        data[7] = (byte)(count << 54);
        final PingKey key = new PingKey(data);
        outstandingPings.put(key, listener);
        if(timeout > 0) {
            http2Channel.getIoThread().executeAfter(() -> {
                PingListener listener1 = outstandingPings.remove(key);
                if(listener1 != null) {
                    listener1.failed(UndertowMessages.MESSAGES.pingTimeout());
                }
            }, timeout, timeUnit);
        }
        http2Channel.sendPing(data, (channel, exception) -> listener.failed(exception));
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
                    ((Http2StreamSourceChannel) result).setTrailersHandler(new Http2StreamSourceChannel.TrailersHandler() {
                        @Override
                        public void handleTrailers(HeaderMap headerMap) {
                            request.putAttachment(HttpAttachments.REQUEST_TRAILERS, headerMap);
                        }
                    });

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
                    Http2ClientExchange exchange = currentExchanges.remove(stream);

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
                            // if no push handler just reset the stream
                            channel.sendRstStream(stream.getPushedStreamId(), Http2Channel.ERROR_REFUSED_STREAM);
                            IoUtils.safeClose(stream);
                        } else if (!http2Channel.addPushPromiseStream(stream.getPushedStreamId())) {
                            // if invalid stream id send connection error of type PROTOCOL_ERROR as spec
                            channel.sendGoAway(Http2Channel.ERROR_PROTOCOL_ERROR);
                        } else {
                            // add the pushed stream to current exchanges
                            currentExchanges.put(stream.getPushedStreamId(), newExchange);
                        }
                    }
                    Channels.drain(result, Long.MAX_VALUE);

                } else if (result instanceof Http2GoAwayStreamSourceChannel) {
                    close();
                } else if(result != null) {
                    Channels.drain(result, Long.MAX_VALUE);
                }

            } catch (Throwable t) {
                IOException e = t instanceof IOException ? (IOException) t : new IOException(t);
                UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                IoUtils.safeClose(Http2ClientConnection.this);
                for (Map.Entry<Integer, Http2ClientExchange> entry : currentExchanges.entrySet()) {
                    try {
                        entry.getValue().failed(e);
                    } catch (Throwable ex) {
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
            } else {
                PingListener listener = outstandingPings.remove(new PingKey(id));
                if(listener != null) {
                    listener.acknowledged();
                }
            }
        }

    }

    private static final class PingKey{
        private final byte[] data;

        private PingKey(byte[] data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PingKey pingKey = (PingKey) o;

            return Arrays.equals(data, pingKey.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }
}
