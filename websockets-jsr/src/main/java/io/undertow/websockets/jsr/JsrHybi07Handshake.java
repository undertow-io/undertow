/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets.jsr;

import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.protocol.version07.Hybi07Handshake;

import javax.websocket.server.ServerEndpointConfiguration;
import java.util.Arrays;
import java.util.Collections;

/**
 * {@link Hybi07Handshake} sub-class which takes care of match against the {@link ServerEndpointConfiguration} and
 * stored the config in the attributes for later usage.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class JsrHybi07Handshake extends Hybi07Handshake {
    private final ServerEndpointConfiguration config;

    public JsrHybi07Handshake(ServerEndpointConfiguration config) {
        super(Collections.<String>emptySet(), false);
        this.config = config;
    }

    @Override
    protected void upgradeChannel(final HttpServerExchange exchange, byte[] data) {
        HandshakeUtil.prepareUpgrade(config, exchange);
        super.upgradeChannel(exchange, data);
    }

    @Override
    public WebSocketChannel createChannel(HttpServerExchange exchange) {
        WebSocketChannel channel =  super.createChannel(exchange);
        HandshakeUtil.setConfig(channel, config);
        return channel;
    }

    @Override
    public boolean matches(HttpServerExchange exchange) {
        return super.matches(exchange) && HandshakeUtil.matches(config, exchange);
    }

    @Override
    protected String supportedSubprotols(String[] requestedSubprotocolArray) {
        return config.getNegotiatedSubprotocol(Arrays.asList(requestedSubprotocolArray));
    }
}
