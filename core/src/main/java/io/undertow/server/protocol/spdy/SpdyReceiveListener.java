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
import io.undertow.server.ConnectorStatisticsImpl;
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
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The recieve listener for a SPDY connection.
 * <p>
 * A new instance is created per connection.
 *
 * @author Stuart Douglas
 */
public class SpdyReceiveListener implements ChannelListener<SpdyChannel> {

    static final HttpString METHOD = new HttpString(":method");
    static final HttpString PATH = new HttpString(":path");
    static final HttpString SCHEME = new HttpString(":scheme");
    static final HttpString VERSION = new HttpString(":version");
    static final HttpString HOST = new HttpString(":host");

    private final HttpHandler rootHandler;
    private final long maxEntitySize;
    private final OptionMap undertowOptions;
    private final String encoding;
    private final boolean decode;
    private final StringBuilder decodeBuffer = new StringBuilder();
    private final boolean allowEncodingSlash;
    private final int bufferSize;
    private final ConnectorStatisticsImpl connectorStatistics;


    public SpdyReceiveListener(HttpHandler rootHandler, OptionMap undertowOptions, int bufferSize, ConnectorStatisticsImpl connectorStatistics) {
        this.rootHandler = rootHandler;
        this.undertowOptions = undertowOptions;
        this.bufferSize = bufferSize;
        this.connectorStatistics = connectorStatistics;
        this.maxEntitySize = undertowOptions.get(UndertowOptions.MAX_ENTITY_SIZE, UndertowOptions.DEFAULT_MAX_ENTITY_SIZE);
        this.allowEncodingSlash = undertowOptions.get(UndertowOptions.ALLOW_ENCODED_SLASH, false);
        this.decode = undertowOptions.get(UndertowOptions.DECODE_URL, true);
        if (undertowOptions.get(UndertowOptions.DECODE_URL, true)) {
            this.encoding = undertowOptions.get(UndertowOptions.URL_CHARSET, StandardCharsets.UTF_8.name());
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
                final SpdyServerConnection connection = new SpdyServerConnection(rootHandler, channel, dataChannel, undertowOptions, bufferSize);


                final HttpServerExchange exchange = new HttpServerExchange(connection, dataChannel.getHeaders(), dataChannel.getResponseChannel().getHeaders(), maxEntitySize);
                connection.setExchange(exchange);
                dataChannel.setMaxStreamSize(maxEntitySize);
                exchange.setRequestScheme(exchange.getRequestHeaders().getFirst(SCHEME));
                exchange.getRequestHeaders().remove(SCHEME);
                exchange.setProtocol(new HttpString(exchange.getRequestHeaders().getFirst(VERSION)));
                exchange.getRequestHeaders().remove(VERSION);
                exchange.setRequestMethod(new HttpString(exchange.getRequestHeaders().getFirst(METHOD)));
                exchange.getRequestHeaders().remove(METHOD);
                exchange.getRequestHeaders().put(Headers.HOST, exchange.getRequestHeaders().getFirst(HOST));
                exchange.getRequestHeaders().remove(HOST);
                final String path = exchange.getRequestHeaders().getFirst(PATH);
                exchange.getRequestHeaders().remove(PATH);
                Connectors.setExchangeRequestPath(exchange, path, encoding, decode, allowEncodingSlash, decodeBuffer);

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
                if(connectorStatistics != null) {
                    connectorStatistics.setup(exchange);
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
}
