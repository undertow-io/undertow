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

import static io.undertow.util.Headers.CONTENT_LENGTH;
import static io.undertow.util.Headers.TRANSFER_ENCODING;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.Option;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
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


    static final HttpString METHOD = new HttpString(":method");
    static final HttpString PATH = new HttpString(":path");
    static final HttpString SCHEME = new HttpString(":scheme");
    static final HttpString VERSION = new HttpString(":version");
    static final HttpString HOST = new HttpString(":host");
    static final HttpString STATUS = new HttpString(":status");

    private final Http2Channel http2Channel;
    private final ChannelListener.SimpleSetter<ClientConnection> closeSetter = new ChannelListener.SimpleSetter<>();

    private final Map<Integer, Http2ClientExchange> currentExchanges = new ConcurrentHashMap<>();

    public Http2ClientConnection(Http2Channel http2Channel) {
        this.http2Channel = http2Channel;
        http2Channel.getReceiveSetter().set(new Http2ReceiveListener());
        http2Channel.resumeReceives();
        http2Channel.addCloseTask(new ChannelListener<Http2Channel>() {
            @Override
            public void handleEvent(Http2Channel channel) {
                ChannelListeners.invokeChannelListener(Http2ClientConnection.this, closeSetter.get());
            }
        });
    }

    @Override
    public void sendRequest(ClientRequest request, ClientCallback<ClientExchange> clientCallback) {
        request.getRequestHeaders().put(PATH, request.getPath());
        request.getRequestHeaders().put(SCHEME, "https");
        request.getRequestHeaders().put(VERSION, request.getProtocol().toString());
        request.getRequestHeaders().put(METHOD, request.getMethod().toString());
        request.getRequestHeaders().put(HOST, request.getRequestHeaders().getFirst(Headers.HOST));
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
        } else if (transferEncodingString == null) {
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
            request.getRequestHeaders().put(Headers.X_FORWARDED_HOST, hn);
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
    public Pool<ByteBuffer> getBufferPool() {
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
        return http2Channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        http2Channel.sendGoAway(0);
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

    private class Http2ReceiveListener implements ChannelListener<Http2Channel> {

        @Override
        public void handleEvent(Http2Channel channel) {
            try {
                AbstractHttp2StreamSourceChannel result = channel.receive();
                if (result instanceof Http2StreamSourceChannel) {
                    Http2ClientExchange request = currentExchanges.remove(((Http2StreamSourceChannel) result).getStreamId());
                    if (request == null) {
                        //server side initiated stream, we can't deal with that at the moment
                        //just fail
                        //TODO: either handle this properly or at the very least send RST_STREAM
                        IoUtils.safeClose(Http2ClientConnection.this);
                        return;
                    }
                    request.responseReady((Http2StreamSourceChannel) result);

                } else if (result instanceof Http2PingStreamSourceChannel) {
                    handlePing((Http2PingStreamSourceChannel) result);
                } else if (result instanceof Http2RstStreamStreamSourceChannel) {
                    int stream = ((Http2RstStreamStreamSourceChannel)result).getStreamId();
                    UndertowLogger.REQUEST_LOGGER.debugf("Client received RST_STREAM for stream %s", stream);
                    Http2ClientExchange exchange = currentExchanges.get(stream);
                    if(exchange != null) {
                        exchange.failed(UndertowMessages.MESSAGES.http2StreamWasReset());
                    }
                } else if(!channel.isOpen()) {
                    throw UndertowMessages.MESSAGES.channelIsClosed();
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
