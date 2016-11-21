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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.net.ssl.SSLSession;

import io.undertow.server.ConnectorStatisticsImpl;
import io.undertow.util.Methods;
import io.undertow.util.Protocols;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.protocols.http2.AbstractHttp2StreamSourceChannel;
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.protocols.http2.Http2DataStreamSinkChannel;
import io.undertow.protocols.http2.Http2HeadersStreamSinkChannel;
import io.undertow.protocols.http2.Http2StreamSourceChannel;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.ParameterLimitException;
import io.undertow.util.StatusCodes;
import org.xnio.channels.Channels;

/**
 * The recieve listener for a Http2 connection.
 * <p>
 * A new instance is created per connection.
 *
 * @author Stuart Douglas
 */
public class Http2ReceiveListener implements ChannelListener<Http2Channel> {

    static final HttpString METHOD = new HttpString(":method");
    static final HttpString PATH = new HttpString(":path");
    static final HttpString SCHEME = new HttpString(":scheme");
    static final HttpString AUTHORITY = new HttpString(":authority");

    private final HttpHandler rootHandler;
    private final long maxEntitySize;
    private final OptionMap undertowOptions;
    private final String encoding;
    private final boolean decode;
    private final StringBuilder decodeBuffer = new StringBuilder();
    private final boolean allowEncodingSlash;
    private final int bufferSize;
    private final int maxParameters;



    private final ConnectorStatisticsImpl connectorStatistics;

    private static final AtomicIntegerFieldUpdater<Http2ReceiveListener> concurrentRequestsUpdater = AtomicIntegerFieldUpdater.newUpdater(Http2ReceiveListener.class, "concurrentRequests");

    /**
     * Field that is used to track concurrent requests. Only used if the max concurrent requests option is set
     */
    private volatile int concurrentRequests;


    public Http2ReceiveListener(HttpHandler rootHandler, OptionMap undertowOptions, int bufferSize, ConnectorStatisticsImpl connectorStatistics) {
        this.rootHandler = rootHandler;
        this.undertowOptions = undertowOptions;
        this.bufferSize = bufferSize;
        this.connectorStatistics = connectorStatistics;
        this.maxEntitySize = undertowOptions.get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE);
        this.allowEncodingSlash = undertowOptions.get(UndertowOptions.ALLOW_ENCODED_SLASH, false);
        this.decode = undertowOptions.get(UndertowOptions.DECODE_URL, true);
        this.maxParameters = undertowOptions.get(UndertowOptions.MAX_PARAMETERS, UndertowOptions.DEFAULT_MAX_PARAMETERS);
        if (undertowOptions.get(UndertowOptions.DECODE_URL, true)) {
            this.encoding = undertowOptions.get(UndertowOptions.URL_CHARSET, StandardCharsets.UTF_8.name());
        } else {
            this.encoding = null;
        }
    }

    @Override
    public void handleEvent(Http2Channel channel) {

        try {
            final AbstractHttp2StreamSourceChannel frame = channel.receive();
            if (frame == null) {
                return;
            }
            if (frame instanceof Http2StreamSourceChannel) {

                handleRequests(channel, (Http2StreamSourceChannel) frame);

            }

        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            IoUtils.safeClose(channel);
        }
    }

    private void handleRequests(Http2Channel channel, Http2StreamSourceChannel frame) {
        //we have a request
        final Http2StreamSourceChannel dataChannel = frame;
        final Http2ServerConnection connection = new Http2ServerConnection(channel, dataChannel, undertowOptions, bufferSize, rootHandler);

        if(!dataChannel.getHeaders().contains(SCHEME) ||
                !dataChannel.getHeaders().contains(METHOD) ||
                !dataChannel.getHeaders().contains(AUTHORITY) ||
                !dataChannel.getHeaders().contains(PATH)) {
            channel.sendRstStream(frame.getStreamId(), Http2Channel.ERROR_PROTOCOL_ERROR);
            try {
                Channels.drain(frame, Long.MAX_VALUE);
            } catch (IOException e) {
                //ignore, this is expected because of the RST
            }
            return;
        }


        final HttpServerExchange exchange = new HttpServerExchange(connection, dataChannel.getHeaders(), dataChannel.getResponseChannel().getHeaders(), maxEntitySize);
        connection.setExchange(exchange);
        dataChannel.setMaxStreamSize(maxEntitySize);
        exchange.setRequestScheme(exchange.getRequestHeaders().getFirst(SCHEME));
        exchange.setProtocol(Protocols.HTTP_2_0);
        exchange.setRequestMethod(Methods.fromString(exchange.getRequestHeaders().getFirst(METHOD)));
        exchange.getRequestHeaders().put(Headers.HOST, exchange.getRequestHeaders().getFirst(AUTHORITY));

        final String path = exchange.getRequestHeaders().getFirst(PATH);
        try {
            Connectors.setExchangeRequestPath(exchange, path, encoding, decode, allowEncodingSlash, decodeBuffer, maxParameters);
        } catch (ParameterLimitException e) {
            //this can happen if max parameters is exceeded
            UndertowLogger.REQUEST_IO_LOGGER.debug("Failed to set request path", e);
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            exchange.endExchange();
            return;
        }
        SSLSession session = channel.getSslSession();
        if(session != null) {
            connection.setSslSessionInfo(new Http2SslSessionInfo(channel));
        }
        dataChannel.getResponseChannel().setCompletionListener(new ChannelListener<Http2DataStreamSinkChannel>() {
            @Override
            public void handleEvent(Http2DataStreamSinkChannel channel) {
                Connectors.terminateResponse(exchange);
            }
        });
        if(!dataChannel.isOpen()) {
            Connectors.terminateRequest(exchange);
        } else {
            dataChannel.setCompletionListener(new ChannelListener<Http2StreamSourceChannel>() {
                @Override
                public void handleEvent(Http2StreamSourceChannel channel) {
                    Connectors.terminateRequest(exchange);
                }
            });
        }
        if(connectorStatistics != null) {
            connectorStatistics.setup(exchange);
        }

        //TODO: we should never actually put these into the map in the first place
        exchange.getRequestHeaders().remove(AUTHORITY);
        exchange.getRequestHeaders().remove(PATH);
        exchange.getRequestHeaders().remove(SCHEME);
        exchange.getRequestHeaders().remove(METHOD);


        Connectors.executeRootHandler(rootHandler, exchange);
    }

    /**
     * Handles the initial request when the exchange was started by a HTTP ugprade.
     *
     *
     * @param initial The initial upgrade request that started the HTTP2 connection
     */
    void handleInitialRequest(HttpServerExchange initial, Http2Channel channel) {

        //we have a request
        Http2HeadersStreamSinkChannel sink = channel.createInitialUpgradeResponseStream();
        final Http2ServerConnection connection = new Http2ServerConnection(channel, sink, undertowOptions, bufferSize, rootHandler);

        HeaderMap requestHeaders = new HeaderMap();
        for(HeaderValues hv : initial.getRequestHeaders()) {
            requestHeaders.putAll(hv.getHeaderName(), hv);
        }
        final HttpServerExchange exchange = new HttpServerExchange(connection, requestHeaders, sink.getHeaders(), maxEntitySize);
        connection.setExchange(exchange);
        exchange.setRequestScheme(initial.getRequestScheme());
        exchange.setProtocol(initial.getProtocol());
        exchange.setRequestMethod(initial.getRequestMethod());
        exchange.setQueryString(initial.getQueryString());
        String uri = exchange.getQueryString().isEmpty() ? initial.getRequestURI() : initial.getRequestURI() + '?' + exchange.getQueryString();
        try {
            Connectors.setExchangeRequestPath(exchange, uri, encoding, decode, allowEncodingSlash, decodeBuffer, maxParameters);
        } catch (ParameterLimitException e) {
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            exchange.endExchange();
            return;
        }

        SSLSession session = channel.getSslSession();
        if(session != null) {
            connection.setSslSessionInfo(new Http2SslSessionInfo(channel));
        }
        Connectors.terminateRequest(exchange);
        sink.setCompletionListener(new ChannelListener<Http2DataStreamSinkChannel>() {
            @Override
            public void handleEvent(Http2DataStreamSinkChannel channel) {
                Connectors.terminateResponse(exchange);
            }
        });
        Connectors.executeRootHandler(rootHandler, exchange);
    }

}
