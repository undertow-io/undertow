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

package io.undertow.server.protocol.http2;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.net.ssl.SSLSession;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.protocols.http2.Http2HeadersStreamSinkChannel;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.XnioBufferPoolAdaptor;
import io.undertow.server.protocol.http.HttpContinue;
import io.undertow.util.ConduitFactory;
import io.undertow.util.DateUtils;
import io.undertow.util.Headers;
import io.undertow.util.ParameterLimitException;
import io.undertow.util.Protocols;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.Configurable;
import org.xnio.channels.ConnectedChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.EmptyStreamSourceConduit;
import org.xnio.conduits.StreamSinkChannelWrappingConduit;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceChannelWrappingConduit;
import org.xnio.conduits.StreamSourceConduit;
import org.xnio.conduits.WriteReadyHandler;

import io.undertow.UndertowMessages;
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.protocols.http2.Http2DataStreamSinkChannel;
import io.undertow.protocols.http2.Http2StreamSourceChannel;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.server.SSLSessionInfo;
import io.undertow.server.ServerConnection;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;
import io.undertow.util.BadRequestException;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import io.undertow.util.URLUtils;

import static io.undertow.protocols.http2.Http2Channel.AUTHORITY;
import static io.undertow.protocols.http2.Http2Channel.METHOD;
import static io.undertow.protocols.http2.Http2Channel.PATH;
import static io.undertow.protocols.http2.Http2Channel.SCHEME;

/**
 * A server connection. There is one connection per request
 *
 *
 * TODO: how are we going to deal with attachments?
 * @author Stuart Douglas
 */
public class Http2ServerConnection extends ServerConnection {

    private static final HttpString STATUS = new HttpString(":status");

    private final Http2Channel channel;
    private final Http2StreamSourceChannel requestChannel;
    private final Http2DataStreamSinkChannel responseChannel;
    private final ConduitStreamSinkChannel conduitStreamSinkChannel;
    private final ConduitStreamSourceChannel conduitStreamSourceChannel;
    private final StreamSinkConduit originalSinkConduit;
    private final StreamSourceConduit originalSourceConduit;
    private final OptionMap undertowOptions;
    private final int bufferSize;
    private SSLSessionInfo sessionInfo;
    private final HttpHandler rootHandler;
    private HttpServerExchange exchange;
    private boolean continueSent = false;
    private XnioBufferPoolAdaptor poolAdaptor;
    private final String protocolRequestId;

    public Http2ServerConnection(Http2Channel channel, Http2StreamSourceChannel requestChannel, OptionMap undertowOptions, int bufferSize, HttpHandler rootHandler) {
        this.channel = channel;
        this.requestChannel = requestChannel;
        this.undertowOptions = undertowOptions;
        this.bufferSize = bufferSize;
        this.rootHandler = rootHandler;
        responseChannel = requestChannel.getResponseChannel();
        originalSinkConduit = new StreamSinkChannelWrappingConduit(responseChannel);
        originalSourceConduit = new StreamSourceChannelWrappingConduit(requestChannel);
        this.conduitStreamSinkChannel = new ConduitStreamSinkChannel(responseChannel, originalSinkConduit);
        this.conduitStreamSourceChannel = new ConduitStreamSourceChannel(channel, originalSourceConduit);
        this.protocolRequestId = channel.getProtocolRequestId();
    }

    void setExchange(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    /**
     * Channel that is used when the request is already half closed
     * @param channel
     * @param undertowOptions
     * @param bufferSize
     * @param rootHandler
     */
    public Http2ServerConnection(Http2Channel channel, Http2DataStreamSinkChannel sinkChannel, OptionMap undertowOptions, int bufferSize, HttpHandler rootHandler) {
        this.channel = channel;
        this.rootHandler = rootHandler;
        this.requestChannel = null;
        this.undertowOptions = undertowOptions;
        this.bufferSize = bufferSize;
        responseChannel = sinkChannel;
        originalSinkConduit = new StreamSinkChannelWrappingConduit(responseChannel);
        originalSourceConduit = new StreamSourceChannelWrappingConduit(requestChannel);
        this.conduitStreamSinkChannel = new ConduitStreamSinkChannel(responseChannel, originalSinkConduit);
        this.conduitStreamSourceChannel = new ConduitStreamSourceChannel(Configurable.EMPTY, new EmptyStreamSourceConduit(getIoThread()));
        this.protocolRequestId = channel.getProtocolRequestId();
    }

    @Override
    public Pool<ByteBuffer> getBufferPool() {
        if(poolAdaptor == null) {
            poolAdaptor = new XnioBufferPoolAdaptor(getByteBufferPool());
        }
        return poolAdaptor;
    }

    public String getProtocolRequestId() {
        return protocolRequestId;
    }

    public SSLSession getSslSession() {
        return channel.getSslSession();
    }

    @Override
    public ByteBufferPool getByteBufferPool() {
        return channel.getBufferPool();
    }

    @Override
    public XnioWorker getWorker() {
        return channel.getWorker();
    }

    @Override
    public XnioIoThread getIoThread() {
        return channel.getIoThread();
    }

    @Override
    public HttpServerExchange sendOutOfBandResponse(HttpServerExchange exchange) {

        if (exchange == null || !HttpContinue.requiresContinueResponse(exchange)) {
            throw UndertowMessages.MESSAGES.outOfBandResponseOnlyAllowedFor100Continue();
        }
        final HttpServerExchange newExchange = new HttpServerExchange(this);
        for (HttpString header : exchange.getRequestHeaders().getHeaderNames()) {
            newExchange.getRequestHeaders().putAll(header, exchange.getRequestHeaders().get(header));
        }
        newExchange.setProtocol(exchange.getProtocol());
        newExchange.setRequestMethod(exchange.getRequestMethod());
        exchange.setRequestURI(exchange.getRequestURI(), exchange.isHostIncludedInRequestURI());
        exchange.setRequestPath(exchange.getRequestPath());
        exchange.setRelativePath(exchange.getRelativePath());
        newExchange.setPersistent(true);

        Connectors.terminateRequest(newExchange);
        newExchange.addResponseWrapper(new ConduitWrapper<StreamSinkConduit>() {
            @Override
            public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {

                HeaderMap headers = newExchange.getResponseHeaders();
                DateUtils.addDateHeaderIfRequired(exchange);
                headers.add(STATUS, exchange.getStatusCode());
                Connectors.flattenCookies(exchange);
                Http2HeadersStreamSinkChannel sink = new Http2HeadersStreamSinkChannel(channel, requestChannel.getStreamId(), headers);

                StreamSinkChannelWrappingConduit ret = new StreamSinkChannelWrappingConduit(sink);
                ret.setWriteReadyHandler(new WriteReadyHandler.ChannelListenerHandler(Connectors.getConduitSinkChannel(exchange)));
                return ret;
            }
        });
        continueSent = true;
        return newExchange;

    }

    @Override
    public boolean isContinueResponseSupported() {
        return true;
    }

    @Override
    public void terminateRequestChannel(HttpServerExchange exchange) {
        if(HttpContinue.requiresContinueResponse(exchange.getRequestHeaders()) && !continueSent) {
            if(requestChannel != null) { //can happen on upgrade
                requestChannel.setIgnoreForceClose(true);
                requestChannel.close();
                //if this request requires a 100-continue and it was not sent we have to reset the stream
                //we do it in a completion listener though, to make sure the response is sent first
                exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
                    @Override
                    public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
                        try {
                            channel.sendRstStream(responseChannel.getStreamId(), Http2Channel.ERROR_CANCEL);
                        } finally {
                            nextListener.proceed();
                        }
                    }
                });
            }
        }
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
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
    public void close() throws IOException {
        channel.sendRstStream(requestChannel.getStreamId(), Http2Channel.ERROR_CANCEL);
    }

    @Override
    public SocketAddress getPeerAddress() {
        return channel.getPeerAddress();
    }

    @Override
    public <A extends SocketAddress> A getPeerAddress(Class<A> type) {
        return channel.getPeerAddress(type);
    }

    @Override
    public ChannelListener.Setter<? extends ConnectedChannel> getCloseSetter() {
        return channel.getCloseSetter();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    @Override
    public <A extends SocketAddress> A getLocalAddress(Class<A> type) {
        return channel.getLocalAddress(type);
    }

    @Override
    public OptionMap getUndertowOptions() {
        return undertowOptions;
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public SSLSessionInfo getSslSessionInfo() {
        return sessionInfo;
    }

    @Override
    public void setSslSessionInfo(SSLSessionInfo sessionInfo) {
        this.sessionInfo = sessionInfo;
    }

    @Override
    public void addCloseListener(final CloseListener listener) {
        channel.addCloseTask(new ChannelListener<Http2Channel>() {
            @Override
            public void handleEvent(Http2Channel channel) {
                listener.closed(Http2ServerConnection.this);
            }
        });
    }

    @Override
    protected StreamConnection upgradeChannel() {
        throw UndertowMessages.MESSAGES.upgradeNotSupported();
    }

    @Override
    protected ConduitStreamSinkChannel getSinkChannel() {
        return conduitStreamSinkChannel;
    }

    @Override
    protected ConduitStreamSourceChannel getSourceChannel() {
        return conduitStreamSourceChannel;
    }

    @Override
    protected StreamSinkConduit getSinkConduit(HttpServerExchange exchange, StreamSinkConduit conduit) {
        HeaderMap headers = responseChannel.getHeaders();
        DateUtils.addDateHeaderIfRequired(exchange);
        headers.add(STATUS, exchange.getStatusCode());
        Connectors.flattenCookies(exchange);
        if(!Connectors.isEntityBodyAllowed(exchange)) {
            //we are not allowed to send an entity body for some requests
            exchange.getResponseHeaders().remove(Headers.CONTENT_LENGTH);
            exchange.getResponseHeaders().remove(Headers.TRANSFER_ENCODING);
        }
        return originalSinkConduit;
    }

    @Override
    protected boolean isUpgradeSupported() {
        return false;
    }

    @Override
    protected boolean isConnectSupported() {
        return false;
    }

    @Override
    protected void exchangeComplete(HttpServerExchange exchange) {
    }

    @Override
    protected void setUpgradeListener(HttpUpgradeListener upgradeListener) {
        throw UndertowMessages.MESSAGES.upgradeNotSupported();
    }

    @Override
    protected void setConnectListener(HttpUpgradeListener connectListener) {

    }

    @Override
    protected void maxEntitySizeUpdated(HttpServerExchange exchange) {
        if(requestChannel != null) {
            requestChannel.setMaxStreamSize(exchange.getMaxEntitySize());
        }
    }

    @Override
    public <T> void addToAttachmentList(AttachmentKey<AttachmentList<T>> key, T value) {
        channel.addToAttachmentList(key, value);
    }

    @Override
    public <T> T removeAttachment(AttachmentKey<T> key) {
        return channel.removeAttachment(key);
    }

    @Override
    public <T> T putAttachment(AttachmentKey<T> key, T value) {
        return channel.putAttachment(key, value);
    }

    @Override
    public <T> List<T> getAttachmentList(AttachmentKey<? extends List<T>> key) {
        return channel.getAttachmentList(key);
    }

    @Override
    public <T> T getAttachment(AttachmentKey<T> key) {
        return channel.getAttachment(key);
    }

    @Override
    public boolean isPushSupported() {
        return channel.isPushEnabled()
                && !exchange.getRequestHeaders().contains(Headers.X_DISABLE_PUSH)
                // push is not supported for already pushed streams, just for peer-initiated (odd) ids
                && responseChannel.getStreamId() % 2 != 0;
    }

    @Override
    public boolean isRequestTrailerFieldsSupported() {
        return true;
    }

    @Override
    public boolean pushResource(String path, HttpString method, HeaderMap requestHeaders) {
        return pushResource(path, method, requestHeaders, rootHandler);
    }

    @Override
    public boolean pushResource(String path, HttpString method, HeaderMap requestHeaders, final HttpHandler handler) {
        HeaderMap responseHeaders = new HeaderMap();
        try {
            requestHeaders.put(METHOD, method.toString());
            requestHeaders.put(PATH, path.toString());
            requestHeaders.put(AUTHORITY, exchange.getHostAndPort());
            requestHeaders.put(SCHEME, exchange.getRequestScheme());

            Http2HeadersStreamSinkChannel sink = channel.sendPushPromise(responseChannel.getStreamId(), requestHeaders, responseHeaders);
            Http2ServerConnection newConnection = new Http2ServerConnection(channel, sink, getUndertowOptions(), getBufferSize(), rootHandler);
            final HttpServerExchange exchange = new HttpServerExchange(newConnection, requestHeaders, responseHeaders, getUndertowOptions().get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE));
            newConnection.setExchange(exchange);
            exchange.setRequestMethod(method);
            exchange.setProtocol(Protocols.HTTP_1_1);
            exchange.setRequestScheme(this.exchange.getRequestScheme());
            try {
                Connectors.setExchangeRequestPath(exchange, path, getUndertowOptions().get(UndertowOptions.URL_CHARSET, StandardCharsets.UTF_8.name()), getUndertowOptions().get(UndertowOptions.DECODE_URL, true), URLUtils.getSlashDecodingFlag(getUndertowOptions()), new StringBuilder(), getUndertowOptions().get(UndertowOptions.MAX_PARAMETERS, UndertowOptions.DEFAULT_MAX_HEADERS));
            } catch (ParameterLimitException | BadRequestException e) {
                UndertowLogger.REQUEST_IO_LOGGER.debug("Too many parameters in HTTP/2 request", e);
                exchange.setStatusCode(StatusCodes.BAD_REQUEST);
                exchange.endExchange();
                return false;
            }

            sink.setCompletionListener(new ChannelListener<Http2DataStreamSinkChannel>() {
                @Override
                public void handleEvent(Http2DataStreamSinkChannel channel) {
                    Connectors.terminateResponse(exchange);
                }
            });
            Connectors.terminateRequest(exchange);
            getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    Connectors.executeRootHandler(handler, exchange);
                }
            });
            return true;
        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            return false;
        }
    }

    @Override
    public String getTransportProtocol() {
        return channel.getProtocol();
    }
}
