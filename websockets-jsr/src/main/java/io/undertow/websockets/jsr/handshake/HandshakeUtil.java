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
package io.undertow.websockets.jsr.handshake;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.websockets.ServletWebSocketHttpExchange;
import jakarta.websocket.Extension;
import jakarta.websocket.server.ServerEndpointConfig;

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
    private static final AttachmentKey<ServerEndpointConfig> PER_SESSION_CONFIG = AttachmentKey.create(ServerEndpointConfig.class);


    public static final AttachmentKey<Map<String, String>> PATH_PARAMS = AttachmentKey.create(Map.class);
    public static final AttachmentKey<Principal> PRINCIPAL = AttachmentKey.create(Principal.class);

    private HandshakeUtil() {
    }

    /**
     * Checks the orgin against the
     */
    public static boolean checkOrigin(ServerEndpointConfig config, WebSocketHttpExchange exchange) {
        ServerEndpointConfig.Configurator c = config.getConfigurator();
        return c.checkOrigin(exchange.getRequestHeader(Headers.ORIGIN_STRING));
    }

    /**
     * Prepare for upgrade
     */
    public static void prepareUpgrade(final ServerEndpointConfig config, final WebSocketHttpExchange exchange) {
        final ServerEndpointConfig perSessionConfig = new PerConnectionServerEndpointConfig(config);
        exchange.putAttachment(PER_SESSION_CONFIG, perSessionConfig);
        ExchangeHandshakeRequest request = new ExchangeHandshakeRequest(exchange);
        ExchangeHandshakeResponse response = new ExchangeHandshakeResponse(exchange);
        ServerEndpointConfig.Configurator c = perSessionConfig.getConfigurator();
        c.modifyHandshake(perSessionConfig, request, response);
        response.update();
    }

    /**
     * Set the {@link ConfiguredServerEndpoint} which is used to create the {@link WebSocketChannel}.
     */
    public static void setConfig(final WebSocketChannel channel, final ConfiguredServerEndpoint config, final WebSocketHttpExchange exchange) {
        final ServerEndpointConfig perSessionConfig = exchange.getAttachment(PER_SESSION_CONFIG);
        channel.setAttribute(CONFIG_KEY, new PerConnectionConfiguredServerEndpoint(config, perSessionConfig));
    }

    /**
     * Set the {@link ConfiguredServerEndpoint} which is used to create the {@link WebSocketChannel}.
     * @Deprecated Use {@link HandshakeUtil#setConfig(WebSocketChannel, ConfiguredServerEndpoint, WebSocketHttpExchange)} instead
     */
    @Deprecated
    public static void setConfig(WebSocketChannel channel, ConfiguredServerEndpoint config) {
        channel.setAttribute(CONFIG_KEY, config);
    }

    /**
     * Returns the {@link ConfiguredServerEndpoint} which was used while create the {@link WebSocketChannel}.
     */
    public static ConfiguredServerEndpoint getConfig(WebSocketChannel channel) {
        return (ConfiguredServerEndpoint) channel.getAttribute(CONFIG_KEY);
    }

    public static void propagate(final HttpServerExchange source, final ServletWebSocketHttpExchange target) {
        final ServerEndpointConfig perSession = source.getAttachment(PER_SESSION_CONFIG);
        target.putAttachment(PER_SESSION_CONFIG, perSession);
    }

    static String selectSubProtocol(final ConfiguredServerEndpoint config, final String[] requestedSubprotocolArray) {
        if (config.getEndpointConfiguration().getConfigurator() != null) {
            return config.getEndpointConfiguration().getConfigurator().getNegotiatedSubprotocol(config.getEndpointConfiguration().getSubprotocols(), Arrays.asList(requestedSubprotocolArray));
        } else {
            for (final String protocol : config.getEndpointConfiguration().getSubprotocols()) {
                for (String clientsupported : requestedSubprotocolArray) {
                    if (protocol.equals(clientsupported)) {
                        return protocol;
                    }
                }
            }
            return null;
        }
    }

    static List<Extension> selectExtensions(final ConfiguredServerEndpoint config, final List<Extension> requestedExtensions) {
        if (config.getEndpointConfiguration().getConfigurator() != null) {
            return config.getEndpointConfiguration().getConfigurator().getNegotiatedExtensions(config.getExtensions(), requestedExtensions);
        } else {
            return Collections.emptyList();
        }
    }
}
