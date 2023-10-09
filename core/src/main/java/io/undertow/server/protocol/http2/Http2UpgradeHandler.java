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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.xnio.OptionMap;
import org.xnio.StreamConnection;

import io.undertow.UndertowLogger;
import io.undertow.UndertowOptions;
import io.undertow.io.IoCallback;
import io.undertow.io.Receiver;
import io.undertow.io.Sender;
import io.undertow.protocols.http2.Http2Channel;
import io.undertow.server.Connectors;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.server.protocol.http.HttpContinue;
import io.undertow.util.FlexBase64;
import io.undertow.util.Headers;
import io.undertow.util.ImmediatePooledByteBuffer;
import io.undertow.util.Protocols;
import io.undertow.util.StatusCodes;

/**
 * Upgrade listener for HTTP2, this allows connections to be established using the upgrade
 * mechanism as detailed in Section 3.2. This should always be the first handler in a handler
 * chain.
 *
 *
 * @author Stuart Douglas
 */
public class Http2UpgradeHandler implements HttpHandler {

    private final HttpHandler next;

    private final Set<String> upgradeStrings;

    public Http2UpgradeHandler(HttpHandler next) {
        this.next = next;
        this.upgradeStrings = Collections.singleton(Http2Channel.CLEARTEXT_UPGRADE_STRING);
    }

    public Http2UpgradeHandler(HttpHandler next, String... upgradeStrings) {
        this.next = next;
        this.upgradeStrings = new HashSet<>(Arrays.asList(upgradeStrings));
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final String upgrade = exchange.getRequestHeaders().getFirst(Headers.UPGRADE);
        final String settings = exchange.getRequestHeaders().getFirst("HTTP2-Settings");
        if(settings != null && upgrade != null && upgradeStrings.contains(upgrade)) {
            if(HttpContinue.requiresContinueResponse(exchange)) {
                HttpContinue.sendContinueResponse(exchange, new IoCallback() {
                    @Override
                    public void onComplete(HttpServerExchange exchange, Sender sender) {
                        try {
                            handleUpgradeBody(exchange, upgrade, settings);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                        exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
                        exchange.endExchange();
                    }
                });
            } else {
                handleUpgradeBody(exchange, upgrade, settings);
            }

            return;
        }
        next.handleRequest(exchange);
    }

    private void handleUpgradeBody(HttpServerExchange exchange, String upgrade, String settings) throws Exception {
        if(exchange.isRequestComplete()) {
            handleHttp2Upgrade(exchange, upgrade, settings, null);
        } else {
            final int maxBufferedSize = exchange.getConnection().getUndertowOptions().get(UndertowOptions.MAX_BUFFERED_REQUEST_SIZE, UndertowOptions.DEFAULT_MAX_BUFFERED_REQUEST_SIZE);
            if(exchange.getRequestContentLength() > maxBufferedSize) {
                //request is too big to buffer
                //we don't upgrade to HTTP/2
                next.handleRequest(exchange);
            } else if(exchange.getRequestContentLength() > 0 && exchange.getRequestContentLength() < maxBufferedSize) {
                //we know it is fine to buffer
                exchange.getRequestReceiver().receiveFullBytes(new Receiver.FullBytesCallback() {
                    @Override
                    public void handle(HttpServerExchange exchange, byte[] message) {
                        try {
                            handleHttp2Upgrade(exchange, upgrade, settings, message);
                        } catch (IOException e) {
                            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                            exchange.endExchange();
                        }
                    }
                });
            } else {
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                exchange.getRequestReceiver().receivePartialBytes(new Receiver.PartialBytesCallback() {
                    @Override
                    public void handle(HttpServerExchange exchange, byte[] message, boolean last) {
                        try {
                            outputStream.write(message);
                            if(last) {
                                handleHttp2Upgrade(exchange, upgrade, settings, outputStream.toByteArray());
                            } else if(outputStream.size() >= maxBufferedSize) {
                                exchange.getRequestReceiver().pause();
                                Connectors.ungetRequestBytes(exchange, new ImmediatePooledByteBuffer(ByteBuffer.wrap(outputStream.toByteArray())));
                                Connectors.resetRequestChannel(exchange);
                                next.handleRequest(exchange);
                            }
                        } catch (IOException e) {
                            UndertowLogger.REQUEST_IO_LOGGER.ioException(e);
                            exchange.endExchange();
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        }
    }

    private void handleHttp2Upgrade(HttpServerExchange exchange, final String upgrade, String settings, final byte[] data) throws IOException {
        //required by spec
        final ByteBuffer settingsFrame = FlexBase64.decodeURL(settings);
        exchange.getResponseHeaders().put(Headers.UPGRADE, upgrade);
        exchange.upgradeChannel(new HttpUpgradeListener() {
            @Override
            public void handleUpgrade(StreamConnection streamConnection, HttpServerExchange exchange) {
                OptionMap undertowOptions = exchange.getConnection().getUndertowOptions();
                Http2Channel channel = new Http2Channel(streamConnection, upgrade, exchange.getConnection().getByteBufferPool(), null, false, true, true, settingsFrame, undertowOptions);
                Http2ReceiveListener receiveListener = new Http2ReceiveListener(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange exchange) throws Exception {
                        //if this header is present we don't actually process the rest of the handler chain
                        //as the request was only to create the initial request
                        if(exchange.getRequestHeaders().contains("X-HTTP2-connect-only")) {
                            exchange.endExchange();
                            return;
                        }
                        exchange.setProtocol(Protocols.HTTP_2_0);
                        next.handleRequest(exchange);
                    }
                }, undertowOptions, exchange.getConnection().getBufferSize(), null);
                channel.getReceiveSetter().set(receiveListener);
                // don't decode requests from upgrade, they are already decoded by the parser for protocol HTTP 1.1 (HttpRequestParser)
                receiveListener.handleInitialRequest(exchange, channel, data, false);
                channel.resumeReceives();
            }
        });
    }

}
