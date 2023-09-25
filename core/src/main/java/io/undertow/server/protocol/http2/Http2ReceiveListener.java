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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import javax.net.ssl.SSLSession;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.conduits.HeadStreamSinkConduit;
import io.undertow.protocols.http2.AbstractHttp2StreamSourceChannel;
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.protocols.http2.Http2DataStreamSinkChannel;
import io.undertow.protocols.http2.Http2HeadersStreamSinkChannel;
import io.undertow.protocols.http2.Http2StreamSourceChannel;
import io.undertow.server.ConduitWrapper;
import io.undertow.server.ConnectorStatisticsImpl;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.HttpAttachments;
import io.undertow.server.protocol.http.HttpContinue;
import io.undertow.server.protocol.http.HttpRequestParser;
import io.undertow.util.BadRequestException;
import io.undertow.util.ConduitFactory;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.ImmediatePooledByteBuffer;
import io.undertow.util.Methods;
import io.undertow.util.ParameterLimitException;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;
import io.undertow.util.URLUtils;

import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.channels.Channels;
import org.xnio.conduits.StreamSinkConduit;

import static io.undertow.protocols.http2.Http2Channel.AUTHORITY;
import static io.undertow.protocols.http2.Http2Channel.METHOD;
import static io.undertow.protocols.http2.Http2Channel.PATH;
import static io.undertow.protocols.http2.Http2Channel.SCHEME;

/**
 * The recieve listener for a Http2 connection.
 * <p>
 * A new instance is created per connection.
 *
 * @author Stuart Douglas
 */
public class Http2ReceiveListener implements ChannelListener<Http2Channel> {

    private final HttpHandler rootHandler;
    private final long maxEntitySize;
    private final OptionMap undertowOptions;
    private final String encoding;
    private final boolean decode;
    private final StringBuilder decodeBuffer = new StringBuilder();
    private final boolean slashDecodingFlag;
    private final int bufferSize;
    private final int maxParameters;
    private final boolean recordRequestStartTime;
    private final boolean allowUnescapedCharactersInUrl;

    private final ConnectorStatisticsImpl connectorStatistics;

    public Http2ReceiveListener(HttpHandler rootHandler, OptionMap undertowOptions, int bufferSize, ConnectorStatisticsImpl connectorStatistics) {
        this.rootHandler = rootHandler;
        this.undertowOptions = undertowOptions;
        this.bufferSize = bufferSize;
        this.connectorStatistics = connectorStatistics;
        this.maxEntitySize = undertowOptions.get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE);
        this.slashDecodingFlag = URLUtils.getSlashDecodingFlag(undertowOptions);
        this.decode = undertowOptions.get(UndertowOptions.DECODE_URL, true);
        this.maxParameters = undertowOptions.get(UndertowOptions.MAX_PARAMETERS, UndertowOptions.DEFAULT_MAX_PARAMETERS);
        this.recordRequestStartTime = undertowOptions.get(UndertowOptions.RECORD_REQUEST_START_TIME, false);
        if (undertowOptions.get(UndertowOptions.DECODE_URL, true)) {
            this.encoding = undertowOptions.get(UndertowOptions.URL_CHARSET, StandardCharsets.UTF_8.name());
        } else {
            this.encoding = null;
        }
        this.allowUnescapedCharactersInUrl = undertowOptions.get(UndertowOptions.ALLOW_UNESCAPED_CHARACTERS_IN_URL, false);
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
        } catch (Throwable t) {
            UndertowLogger.REQUEST_IO_LOGGER.handleUnexpectedFailure(t);
            IoUtils.safeClose(channel);
        }
    }

    private void handleRequests(Http2Channel channel, Http2StreamSourceChannel frame) {
        //we have a request
        final Http2StreamSourceChannel dataChannel = frame;
        final Http2ServerConnection connection = new Http2ServerConnection(channel, dataChannel, undertowOptions, bufferSize, rootHandler);

        // Check request headers.
        if (!checkRequestHeaders(dataChannel.getHeaders())) {
            channel.sendRstStream(frame.getStreamId(), Http2Channel.ERROR_PROTOCOL_ERROR);
            try {
                Channels.drain(frame, Long.MAX_VALUE);
            } catch (IOException e) {
                // ignore, this is expected because of the RST
            }
            return;
        }


        final HttpServerExchange exchange = new HttpServerExchange(connection, dataChannel.getHeaders(), dataChannel.getResponseChannel().getHeaders(), maxEntitySize);


        dataChannel.setTrailersHandler(new Http2StreamSourceChannel.TrailersHandler() {
            @Override
            public void handleTrailers(HeaderMap headerMap) {
                exchange.putAttachment(HttpAttachments.REQUEST_TRAILERS, headerMap);
            }
        });
        connection.setExchange(exchange);
        dataChannel.setMaxStreamSize(maxEntitySize);
        exchange.setRequestScheme(exchange.getRequestHeaders().getFirst(SCHEME));
        exchange.setRequestMethod(Methods.fromString(exchange.getRequestHeaders().getFirst(METHOD)));
        exchange.getRequestHeaders().put(Headers.HOST, exchange.getRequestHeaders().getFirst(AUTHORITY));
        if(!Connectors.areRequestHeadersValid(exchange.getRequestHeaders())) {
            UndertowLogger.REQUEST_IO_LOGGER.debugf("Invalid headers in HTTP/2 request, closing connection. Remote peer %s", connection.getPeerAddress());
            channel.sendGoAway(Http2Channel.ERROR_PROTOCOL_ERROR);
            return;
        }

        final String path = exchange.getRequestHeaders().getFirst(PATH);
        if(path == null || path.isEmpty()) {
            UndertowLogger.REQUEST_IO_LOGGER.debugf("No :path header sent in HTTP/2 request, closing connection. Remote peer %s", connection.getPeerAddress());
            channel.sendGoAway(Http2Channel.ERROR_PROTOCOL_ERROR);
            return;
        }

        if (recordRequestStartTime) {
            Connectors.setRequestStartTime(exchange);
        }
        handleCommonSetup(dataChannel.getResponseChannel(), exchange, connection);
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

        try {
            Connectors.setExchangeRequestPath(exchange, path, encoding, decode, slashDecodingFlag, decodeBuffer, maxParameters);
        } catch (ParameterLimitException | BadRequestException e) {
            //this can happen if max parameters is exceeded
            UndertowLogger.REQUEST_IO_LOGGER.debug("Failed to set request path", e);
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            exchange.endExchange();
            return;
        }

        //TODO: we should never actually put these into the map in the first place
        exchange.getRequestHeaders().remove(AUTHORITY);
        exchange.getRequestHeaders().remove(PATH);
        exchange.getRequestHeaders().remove(SCHEME);
        exchange.getRequestHeaders().remove(METHOD);


        Connectors.executeRootHandler(rootHandler, exchange);
    }

    /**
     * Handles the initial request when the exchange was started by a HTTP upgrade.
     *
     * @param initial The initial upgrade request that started the HTTP2 connection
     */
    void handleInitialRequest(HttpServerExchange initial, Http2Channel channel, byte[] data) {
        //we have a request
        Http2HeadersStreamSinkChannel sink = channel.createInitialUpgradeResponseStream();
        final Http2ServerConnection connection = new Http2ServerConnection(channel, sink, undertowOptions, bufferSize, rootHandler);

        HeaderMap requestHeaders = new HeaderMap();
        for(HeaderValues hv : initial.getRequestHeaders()) {
            requestHeaders.putAll(hv.getHeaderName(), hv);
        }
        final HttpServerExchange exchange = new HttpServerExchange(connection, requestHeaders, sink.getHeaders(), maxEntitySize);
        if(initial.getRequestHeaders().contains(Headers.EXPECT)) {
            HttpContinue.markContinueResponseSent(exchange);
        }
        if(initial.getAttachment(HttpAttachments.REQUEST_TRAILERS) != null) {
            exchange.putAttachment(HttpAttachments.REQUEST_TRAILERS, initial.getAttachment(HttpAttachments.REQUEST_TRAILERS));
        }
        Connectors.setRequestStartTime(initial, exchange);
        connection.setExchange(exchange);
        exchange.setRequestScheme(initial.getRequestScheme());
        exchange.setRequestMethod(initial.getRequestMethod());
        exchange.setQueryString(initial.getQueryString());
        if (data != null) {
            Connectors.ungetRequestBytes(exchange, new ImmediatePooledByteBuffer(ByteBuffer.wrap(data)));
        }
        Connectors.terminateRequest(exchange);
        String uri = exchange.getQueryString().isEmpty() ? initial.getRequestURI() : initial.getRequestURI() + '?' + exchange.getQueryString();
        try {
            Connectors.setExchangeRequestPath(exchange, uri, encoding, decode, slashDecodingFlag, decodeBuffer, maxParameters);
        } catch (ParameterLimitException | BadRequestException e) {
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            exchange.endExchange();
            return;
        }

        handleCommonSetup(sink, exchange, connection);
        Connectors.executeRootHandler(rootHandler, exchange);
    }

    private void handleCommonSetup(Http2HeadersStreamSinkChannel sink, HttpServerExchange exchange, Http2ServerConnection connection) {
        Http2Channel channel = sink.getChannel();
        SSLSession session = channel.getSslSession();
        if(session != null) {
            connection.setSslSessionInfo(new Http2SslSessionInfo(channel));
        }
        sink.setTrailersProducer(new Http2DataStreamSinkChannel.TrailersProducer() {
            @Override
            public HeaderMap getTrailers() {
                Supplier<HeaderMap> supplier = exchange.getAttachment(HttpAttachments.RESPONSE_TRAILER_SUPPLIER);
                if(supplier != null) {
                    return supplier.get();
                }
                return exchange.getAttachment(HttpAttachments.RESPONSE_TRAILERS);
            }
        });
        sink.setCompletionListener(new ChannelListener<Http2DataStreamSinkChannel>() {
            @Override
            public void handleEvent(Http2DataStreamSinkChannel channel) {
                Connectors.terminateResponse(exchange);
            }
        });
        exchange.setProtocol(Protocols.HTTP_2_0);
        if(exchange.getRequestMethod().equals(Methods.HEAD)) {
            exchange.addResponseWrapper(new ConduitWrapper<StreamSinkConduit>() {
                @Override
                public StreamSinkConduit wrap(ConduitFactory<StreamSinkConduit> factory, HttpServerExchange exchange) {
                    return new HeadStreamSinkConduit(factory.create(), null, true);
                }
            });
        }
    }

    /**
     * Performs HTTP2 specification compliance check for headers and pseudo-headers of a current request.
     *
     * @param headers map of the request headers
     * @return true if check was successful, false otherwise
     */
    private boolean checkRequestHeaders(HeaderMap headers) {
        // :method pseudo-header must be present always exactly one time;
        // HTTP2 request MUST NOT contain 'connection' header
        if (headers.count(METHOD) != 1 || headers.contains(Headers.CONNECTION)) {
            return false;
        }

        // if CONNECT type is used, then we expect :method and :authority to be present only;
        // :scheme and :path must not be present
        if (headers.get(METHOD).contains(Methods.CONNECT_STRING)) {
            if (headers.contains(SCHEME) || headers.contains(PATH) || headers.count(AUTHORITY) != 1) {
                return false;
            }
        // For other HTTP methods we expect that :scheme, :method, and :path pseudo-headers are
        // present exactly one time.
        } else if (headers.count(SCHEME) != 1 || headers.count(PATH) != 1) {
            return false;
        }

        // HTTP2 request MAY contain TE header but if so, then only with 'trailers' value.
        if (headers.contains(Headers.TE)) {
            for (String value : headers.get(Headers.TE)) {
                if (!value.equals("trailers")) {
                    return false;
                }
            }
        }

        // verify content of request pseudo-headers. Each header should only have a single value.
        if (headers.contains(PATH)) {
            for (byte b: headers.get(PATH).getFirst().getBytes(ISO_8859_1)) {
                if (!allowUnescapedCharactersInUrl && !HttpRequestParser.isTargetCharacterAllowed((char)b)){
                    return false;
                }
            }
        }

        if (headers.contains(SCHEME)) {
            for (byte b: headers.get(SCHEME).getFirst().getBytes(ISO_8859_1)) {
                if (!Connectors.isValidSchemeCharacter(b)){
                    return false;
                }
            }
        }

        if (headers.contains(AUTHORITY)) {
            for (byte b: headers.get(AUTHORITY).getFirst().getBytes(ISO_8859_1)) {
                if (!HttpRequestParser.isTargetCharacterAllowed((char)b)){
                    return false;
                }
            }
        }

        if (headers.contains(METHOD)) {
            for (byte b: headers.get(METHOD).getFirst().getBytes(ISO_8859_1)) {
                if (!Connectors.isValidTokenCharacter(b)){
                    return false;
                }
            }
        }
        return true;
    }
}
