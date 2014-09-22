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
import io.undertow.UndertowOptions;
import io.undertow.protocols.spdy.SpdyStreamStreamSourceChannel;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.protocols.spdy.SpdyChannel;
import io.undertow.protocols.spdy.SpdyPingStreamSourceChannel;
import io.undertow.protocols.spdy.SpdyStreamSourceChannel;
import io.undertow.protocols.spdy.SpdySynReplyStreamSinkChannel;
import io.undertow.protocols.spdy.SpdySynStreamStreamSourceChannel;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.URLUtils;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import javax.net.ssl.SSLSession;
import java.io.IOException;

/**
 * The recieve listener for a SPDY connection.
 * <p/>
 * A new instance is created per connection.
 *
 * @author Stuart Douglas
 */
public class SpdyReceiveListener implements ChannelListener<SpdyChannel> {

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


    public SpdyReceiveListener(HttpHandler rootHandler, OptionMap undertowOptions, int bufferSize) {
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
    public void handleEvent(SpdyChannel channel) {

        try {
            final SpdyStreamSourceChannel frame = channel.receive();
            if (frame == null) {
                return;
            }
            if (frame instanceof SpdyPingStreamSourceChannel) {
                handlePing((SpdyPingStreamSourceChannel) frame);
            } else if (frame instanceof SpdySynStreamStreamSourceChannel) {
                //we have a request
                final SpdySynStreamStreamSourceChannel dataChannel = (SpdySynStreamStreamSourceChannel) frame;
                final SpdyServerConnection connection = new SpdyServerConnection(channel, dataChannel, undertowOptions, bufferSize);


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
                    connection.setSslSessionInfo(new SpdySslSessionInfo(channel));
                }
                dataChannel.getResponseChannel().setCompletionListener(new ChannelListener<SpdySynReplyStreamSinkChannel>() {
                    @Override
                    public void handleEvent(SpdySynReplyStreamSinkChannel channel) {
                        Connectors.terminateResponse(exchange);
                    }
                });
                if(!dataChannel.isOpen()) {
                    Connectors.terminateRequest(exchange);
                } else {
                    dataChannel.setCompletionListener(new ChannelListener<SpdyStreamStreamSourceChannel>() {
                        @Override
                        public void handleEvent(SpdyStreamStreamSourceChannel channel) {
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

    private void handlePing(SpdyPingStreamSourceChannel frame) {
        int id = frame.getId();
        if (id % 2 == 1) {
            //client side ping, return it
            frame.getSpdyChannel().sendPing(id);
        }
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
