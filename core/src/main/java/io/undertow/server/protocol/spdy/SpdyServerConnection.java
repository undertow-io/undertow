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

package io.undertow.server.protocol.spdy;

import io.undertow.UndertowLogger;
import io.undertow.UndertowMessages;
import io.undertow.UndertowOptions;
import io.undertow.protocols.spdy.SpdyStreamSinkChannel;
import io.undertow.protocols.spdy.SpdySynStreamStreamSinkChannel;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.server.SSLSessionInfo;
import io.undertow.server.ServerConnection;
import io.undertow.protocols.spdy.SpdyChannel;
import io.undertow.protocols.spdy.SpdySynReplyStreamSinkChannel;
import io.undertow.protocols.spdy.SpdySynStreamStreamSourceChannel;
import io.undertow.server.XnioBufferPoolAdaptor;
import io.undertow.util.AttachmentKey;
import io.undertow.util.AttachmentList;
import io.undertow.util.DateUtils;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.OptionMap;
import io.undertow.connector.ByteBufferPool;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.XnioIoThread;
import org.xnio.XnioWorker;
import org.xnio.channels.ConnectedChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.conduits.StreamSinkChannelWrappingConduit;
import org.xnio.conduits.StreamSinkConduit;
import org.xnio.conduits.StreamSourceChannelWrappingConduit;
import org.xnio.conduits.StreamSourceConduit;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A server connection. There is one connection per request
 *
 *
 * TODO: how are we going to deal with attachments?
 * @author Stuart Douglas
 */
public class SpdyServerConnection extends ServerConnection {

    private static final HttpString STATUS = new HttpString(":status");
    private static final HttpString VERSION = new HttpString(":version");

    private final HttpHandler rootHandler;
    private final SpdyChannel channel;
    private final SpdySynStreamStreamSourceChannel requestChannel;
    private final SpdyStreamSinkChannel responseChannel;
    private final ConduitStreamSinkChannel conduitStreamSinkChannel;
    private final ConduitStreamSourceChannel conduitStreamSourceChannel;
    private final StreamSinkConduit originalSinkConduit;
    private final StreamSourceConduit originalSourceConduit;
    private final OptionMap undertowOptions;
    private final int bufferSize;
    private SSLSessionInfo sessionInfo;
    private HttpServerExchange exchange;
    private XnioBufferPoolAdaptor poolAdaptor;

    public SpdyServerConnection(HttpHandler rootHandler, SpdyChannel channel, SpdySynStreamStreamSourceChannel requestChannel, OptionMap undertowOptions, int bufferSize) {
        this.rootHandler = rootHandler;
        this.channel = channel;
        this.requestChannel = requestChannel;
        this.undertowOptions = undertowOptions;
        this.bufferSize = bufferSize;
        responseChannel = requestChannel.getResponseChannel();
        originalSinkConduit = new StreamSinkChannelWrappingConduit(responseChannel);
        originalSourceConduit = new StreamSourceChannelWrappingConduit(requestChannel);
        this.conduitStreamSinkChannel = new ConduitStreamSinkChannel(responseChannel, originalSinkConduit);
        this.conduitStreamSourceChannel = new ConduitStreamSourceChannel(requestChannel, originalSourceConduit);
    }
    public SpdyServerConnection(HttpHandler rootHandler, SpdyChannel channel, SpdySynStreamStreamSinkChannel responseChannel, OptionMap undertowOptions, int bufferSize) {
        this.rootHandler = rootHandler;
        this.channel = channel;
        this.requestChannel = null;
        this.undertowOptions = undertowOptions;
        this.bufferSize = bufferSize;
        this.responseChannel = responseChannel;
        originalSinkConduit = new StreamSinkChannelWrappingConduit(responseChannel);
        originalSourceConduit = new StreamSourceChannelWrappingConduit(requestChannel);
        this.conduitStreamSinkChannel = new ConduitStreamSinkChannel(responseChannel, originalSinkConduit);
        this.conduitStreamSourceChannel = null;
    }


    void setExchange(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public Pool<ByteBuffer> getBufferPool() {
        if(poolAdaptor == null) {
            poolAdaptor = new XnioBufferPoolAdaptor(getByteBufferPool());
        }
        return poolAdaptor;
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
        throw UndertowMessages.MESSAGES.outOfBandResponseNotSupported();
    }

    @Override
    public boolean isContinueResponseSupported() {
        return false;
    }

    @Override
    public void terminateRequestChannel(HttpServerExchange exchange) {
        //todo: should we RST_STREAM in this case
        //channel.sendRstStream(responseChannel.getStreamId(), SpdyChannel.RST_STATUS_CANCEL);
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
        channel.close();
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
        requestChannel.getSpdyChannel().addCloseTask(new ChannelListener<SpdyChannel>() {
            @Override
            public void handleEvent(SpdyChannel channel) {
                listener.closed(SpdyServerConnection.this);
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
        HeaderMap headers;
        if(responseChannel instanceof SpdySynReplyStreamSinkChannel) {
            headers = ((SpdySynReplyStreamSinkChannel)responseChannel).getHeaders();
        } else {
            headers = ((SpdySynStreamStreamSinkChannel)responseChannel).getHeaders();
        }
        DateUtils.addDateHeaderIfRequired(exchange);

        headers.put(STATUS, exchange.getStatusCode() + " " + StatusCodes.getReason(exchange.getStatusCode()));
        headers.put(VERSION, exchange.getProtocol().toString());

        Connectors.flattenCookies(exchange);
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
        throw UndertowMessages.MESSAGES.connectNotSupported();
    }

    @Override
    protected void maxEntitySizeUpdated(HttpServerExchange exchange) {
        requestChannel.setMaxStreamSize(exchange.getMaxEntitySize());
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
    public String getTransportProtocol() {
        return SpdyOpenListener.SPDY_3_1;
    }

    @Override
    public boolean pushResource(String path, HttpString method, HeaderMap requestHeaders) {
        return pushResource(path, method, requestHeaders, rootHandler);
    }

    @Override
    public boolean pushResource(String path, HttpString method, HeaderMap requestHeaders, final HttpHandler handler) {
        HeaderMap responseHeaders = new HeaderMap();
        try {
            responseHeaders.put(SpdyReceiveListener.PATH, path.toString());
            responseHeaders.put(SpdyReceiveListener.HOST, exchange.getHostAndPort());
            responseHeaders.put(SpdyReceiveListener.SCHEME, exchange.getRequestScheme());
            responseHeaders.put(SpdyReceiveListener.METHOD, method.toString());

            SpdySynStreamStreamSinkChannel sink = channel.createStream(requestChannel.getStreamId(),responseHeaders);
            SpdyServerConnection newConnection = new SpdyServerConnection(rootHandler, channel, sink, getUndertowOptions(), getBufferSize());

            final HttpServerExchange exchange = new HttpServerExchange(newConnection, requestHeaders, responseHeaders, getUndertowOptions().get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE));
            newConnection.setExchange(exchange);
            exchange.setRequestMethod(method);
            exchange.setProtocol(Protocols.HTTP_1_1);
            exchange.setRequestScheme(this.exchange.getRequestScheme());
            Connectors.setExchangeRequestPath(exchange, path, getUndertowOptions().get(UndertowOptions.URL_CHARSET, StandardCharsets.UTF_8.name()), getUndertowOptions().get(UndertowOptions.DECODE_URL, true), getUndertowOptions().get(UndertowOptions.ALLOW_ENCODED_SLASH, false), new StringBuilder());

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
    public boolean isPushSupported() {
        return true;
    }
}
