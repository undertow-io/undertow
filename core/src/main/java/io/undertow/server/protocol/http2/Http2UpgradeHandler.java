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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.xnio.OptionMap;
import org.xnio.StreamConnection;

import io.undertow.protocols.http2.Http2Channel;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.util.FlexBase64;
import io.undertow.util.Headers;
import io.undertow.util.Protocols;

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
        if(upgrade != null && upgradeStrings.contains(upgrade)) {
            String settings = exchange.getRequestHeaders().getFirst("HTTP2-Settings");
            if(settings != null) {
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
                        receiveListener.handleInitialRequest(exchange, channel);
                        channel.resumeReceives();
                    }
                });
                return;
            }
        }
        next.handleRequest(exchange);
    }

}
