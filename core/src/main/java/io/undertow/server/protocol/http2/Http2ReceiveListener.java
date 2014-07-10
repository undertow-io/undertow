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
import javax.net.ssl.SSLSession;
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
import io.undertow.util.URLUtils;

/**
 * The recieve listener for a Http2 connection.
 * <p/>
 * A new instance is created per connection.
 *
 * @author Stuart Douglas
 */
public class Http2ReceiveListener implements ChannelListener<Http2Channel> {

    private static final HttpString METHOD = new HttpString(":method");
    private static final HttpString PATH = new HttpString(":path");
    private static final HttpString SCHEME = new HttpString(":scheme");
    private static final HttpString VERSION = new HttpString(":version");
    private static final HttpString HOST = new HttpString(":host");

    private final HttpHandler rootHandler;
    private final long maxEntitySize;
    private final OptionMap undertowOptions;
    private final String encoding;
    private final boolean decode;
    private final StringBuilder decodeBuffer = new StringBuilder();
    private final boolean allowEncodingSlash;
    private final int bufferSize;


    public Http2ReceiveListener(HttpHandler rootHandler, OptionMap undertowOptions, int bufferSize) {
        this.rootHandler = rootHandler;
        this.undertowOptions = undertowOptions;
        this.bufferSize = bufferSize;
        this.maxEntitySize = undertowOptions.get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE);
        this.allowEncodingSlash = undertowOptions.get(UndertowOptions.ALLOW_ENCODED_SLASH, false);
        this.decode = undertowOptions.get(UndertowOptions.DECODE_URL, true);
        if (undertowOptions.get(UndertowOptions.DECODE_URL, true)) {
            this.encoding = undertowOptions.get(UndertowOptions.URL_CHARSET, "UTF-8");
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
                //we have a request
                final Http2StreamSourceChannel dataChannel = (Http2StreamSourceChannel) frame;
                final Http2ServerConnection connection = new Http2ServerConnection(channel, dataChannel, undertowOptions, bufferSize);


                final HttpServerExchange exchange = new HttpServerExchange(connection, dataChannel.getHeaders(), dataChannel.getResponseChannel().getHeaders(), maxEntitySize);
                dataChannel.setMaxStreamSize(maxEntitySize);
                exchange.setRequestScheme(exchange.getRequestHeaders().getFirst(SCHEME));
                exchange.setProtocol(new HttpString(exchange.getRequestHeaders().getFirst(VERSION)));
                exchange.setRequestMethod(new HttpString(exchange.getRequestHeaders().getFirst(METHOD)));
                exchange.getRequestHeaders().put(Headers.HOST, exchange.getRequestHeaders().getFirst(HOST));
                final String path = exchange.getRequestHeaders().getFirst(PATH);
                setRequestPath(exchange, path, encoding, allowEncodingSlash, decodeBuffer);

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

                Connectors.executeRootHandler(rootHandler, exchange);
            }

        } catch (IOException e) {
            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
            IoUtils.safeClose(channel);
        }
    }

    /**
     * Handles the initial request when the exchange was started by a HTTP ugprade.
     *
     *
     * @param initial The initial upgrade request that started the HTTP2 connection
     */
    void handleInitialRequest(HttpServerExchange initial, Http2Channel channel) {

        //we have a request
        Http2HeadersStreamSinkChannel sink = new Http2HeadersStreamSinkChannel(channel, 1);
        final Http2ServerConnection connection = new Http2ServerConnection(channel, sink, undertowOptions, bufferSize);

        HeaderMap requestHeaders = new HeaderMap();
        for(HeaderValues hv : initial.getRequestHeaders()) {
            requestHeaders.putAll(hv.getHeaderName(), hv);
        }
        final HttpServerExchange exchange = new HttpServerExchange(connection, requestHeaders, sink.getHeaders(), maxEntitySize);
        exchange.setRequestScheme(initial.getRequestScheme());
        exchange.setProtocol(initial.getProtocol());
        exchange.setRequestMethod(initial.getRequestMethod());
        setRequestPath(exchange, exchange.getRequestURI(), encoding, allowEncodingSlash, decodeBuffer);

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

    /**
     * Sets the request path and query parameters, decoding to the requested charset.
     *
     * @param exchange    The exchange
     * @param encodedPath        The encoded path
     * @param charset     The charset
     */
    private void setRequestPath(final HttpServerExchange exchange, final String encodedPath, final String charset, final boolean allowEncodedSlash, StringBuilder decodeBuffer) {
        boolean requiresDecode = false;
        for (int i = 0; i < encodedPath.length(); ++i) {
            char c = encodedPath.charAt(i);
            if (c == '?') {
                String part;
                String encodedPart = encodedPath.substring(0, i);
                if (requiresDecode) {
                    part = URLUtils.decode(encodedPart, charset, allowEncodedSlash, decodeBuffer);
                } else {
                    part = encodedPart;
                }
                exchange.setRequestPath(part);
                exchange.setRelativePath(part);
                exchange.setRequestURI(encodedPart);
                final String qs = encodedPath.substring(i + 1);
                exchange.setQueryString(qs);
                URLUtils.parseQueryString(qs, exchange, encoding, decode);
                return;
            } else if(c == ';') {
                String part;
                String encodedPart = encodedPath.substring(0, i);
                if (requiresDecode) {
                    part = URLUtils.decode(encodedPart, charset, allowEncodedSlash, decodeBuffer);
                } else {
                    part = encodedPart;
                }
                exchange.setRequestPath(part);
                exchange.setRelativePath(part);
                exchange.setRequestURI(encodedPart);
                for(int j = i; j < encodedPath.length(); ++j) {
                    if (encodedPath.charAt(j) == '?') {
                        String pathParams = encodedPath.substring(i + 1, j);
                        URLUtils.parsePathParms(pathParams, exchange, encoding, decode);
                        String qs = encodedPath.substring(j + 1);
                        exchange.setQueryString(qs);
                        URLUtils.parseQueryString(qs, exchange, encoding, decode);
                        return;
                    }
                }
                URLUtils.parsePathParms(encodedPath.substring(i + 1), exchange, encoding, decode);
                return;
            } else if(c == '%' || c == '+') {
                requiresDecode = true;
            }
        }

        String part;
        if (requiresDecode) {
            part = URLUtils.decode(encodedPath, charset, allowEncodedSlash, decodeBuffer);
        } else {
            part = encodedPath;
        }
        exchange.setRequestPath(part);
        exchange.setRelativePath(part);
        exchange.setRequestURI(encodedPath);
    }

}
