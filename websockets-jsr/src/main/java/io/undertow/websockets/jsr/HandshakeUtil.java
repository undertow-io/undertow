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
import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;

import javax.websocket.server.ServerEndpointConfiguration;
import java.net.URI;

/**
 * Internal util class for handshaking
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class HandshakeUtil {
    private static final String CONFIG_KEY = "ServerEndpointConfiguration";

    private HandshakeUtil() {
    }

    /**
     * Returns {@code true} if the Handshake should be used for the {@link HttpServerExchange}.
     */
    public static boolean matches(ServerEndpointConfiguration config, HttpServerExchange exchange) {
        return config.checkOrigin(exchange.getRequestHeaders().getFirst(Headers.ORIGIN))
                && config.matchesURI(URI.create(exchange.getRequestURI()));
    }

    /**
     * Prepare for upgrade
     */
    public static void prepareUpgrade(final ServerEndpointConfiguration config, final HttpServerExchange exchange) {
        ExchangeHandshakeRequest request = new ExchangeHandshakeRequest(exchange);
        ExchangeHandshakeResponse response = new ExchangeHandshakeResponse(exchange);
        config.modifyHandshake(request, response);
        request.update();
        response.update();
    }

    /**
     * Set the {@link ServerEndpointConfiguration} which is used to create the {@link WebSocketChannel}.
     */
    public static void setConfig(WebSocketChannel channel, ServerEndpointConfiguration config) {
        channel.setAttribute(CONFIG_KEY, config);
    }

    /**
     * Returns the {@link ServerEndpointConfiguration} which was used while create the {@link WebSocketChannel}.
     */
    public static ServerEndpointConfiguration getConfig(WebSocketChannel channel) {
        return (ServerEndpointConfiguration) channel.getAttribute(CONFIG_KEY);
    }
}
