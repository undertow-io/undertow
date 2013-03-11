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
package io.undertow.websockets.jsr.handshake;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.server.ServerEndpointConfiguration;
import javax.websocket.server.ServerEndpointConfigurator;

import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.jsr.ConfiguredServerEndpoint;
import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * Internal util class for handshaking
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class HandshakeUtil {
    private static final String CONFIG_KEY = "ServerEndpointConfiguration";


    private static final AttachmentKey<Map<String, String>> PATH_PARAMS = AttachmentKey.create(Map.class);

    private HandshakeUtil() {
    }

    /**
     * Returns {@code true} if the Handshake should be used for the {@link io.undertow.websockets.spi.WebSocketHttpExchange}.
     */
    public static boolean matches(ServerEndpointConfiguration config, WebSocketHttpExchange exchange) {
        final Map<String, String> pathParams = new HashMap<>();
        exchange.putAttachment(PATH_PARAMS, pathParams);
        ServerEndpointConfigurator c = config.getServerEndpointConfigurator();
        final URI requestUri = URI.create(exchange.getRequestURI());
        return c.checkOrigin(exchange.getRequestHeader(Headers.ORIGIN_STRING))
                && c.matchesURI(config.getPath(), requestUri, pathParams);
    }

    /**
     * Prepare for upgrade
     */
    public static void prepareUpgrade(final ServerEndpointConfiguration config, final WebSocketHttpExchange exchange) {
        ExchangeHandshakeRequest request = new ExchangeHandshakeRequest(exchange);
        ExchangeHandshakeResponse response = new ExchangeHandshakeResponse(exchange);
        ServerEndpointConfigurator c = config.getServerEndpointConfigurator();
        c.modifyHandshake(config, request, response);
        response.update();
    }

    /**
     * Set the {@link ServerEndpointConfiguration} which is used to create the {@link WebSocketChannel}.
     */
    public static void setConfig(WebSocketChannel channel, ConfiguredServerEndpoint config) {
        channel.setAttribute(CONFIG_KEY, config);
    }

    /**
     * Returns the {@link ServerEndpointConfiguration} which was used while create the {@link WebSocketChannel}.
     */
    public static ConfiguredServerEndpoint getConfig(WebSocketChannel channel) {
        return (ConfiguredServerEndpoint) channel.getAttribute(CONFIG_KEY);
    }
}
