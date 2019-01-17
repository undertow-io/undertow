/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.websockets.jsr.handshake;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.websocket.Extension;

import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionUtil;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker;
import io.undertow.util.Headers;
import io.undertow.websockets.jsr.ConfiguredServerEndpoint;
import io.undertow.websockets.jsr.ExtensionImpl;

/**
 * Abstract base class for doing a WebSocket Handshake.
 *
 * @author Mike Brock
 */
public abstract class Handshake {


    private static final String EXTENSION_SEPARATOR = ",";
    private static final String PARAMETER_SEPARATOR = ";";
    private static final char PARAMETER_EQUAL = '=';

    public static final String MAGIC_NUMBER = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    public static final String SHA1 = "SHA1";
    private static final String WEB_SOCKET_VERSION = "13";

    protected final Set<String> subprotocols;
    private static final byte[] EMPTY = new byte[0];
    private static final Pattern PATTERN = Pattern.compile("\\s*,\\s*");
    protected Set<WebSocketServerExtensionHandshaker> availableExtensions = new HashSet<>();
    protected boolean allowExtensions;
    private final ConfiguredServerEndpoint config;

    protected Handshake(ConfiguredServerEndpoint config, final Set<String> subprotocols) {
        this.subprotocols = subprotocols;
        this.config = config;
    }

    /**
     * Return the full url of the websocket location of the given {@link WebSocketHttpExchange}
     */
    protected static String getWebSocketLocation(WebSocketHttpExchange exchange) {
        String scheme;
        if ("https".equals(exchange.getRequestScheme())) {
            scheme = "wss";
        } else {
            scheme = "ws";
        }
        return scheme + "://" + exchange.getRequestHeader(Headers.HOST_STRING) + exchange.getRequestURI();
    }

    /**
     * Issue the WebSocket upgrade
     *
     * @param exchange The {@link WebSocketHttpExchange} for which the handshake and upgrade should occur.
     */
    public final void handshake(final WebSocketHttpExchange exchange) {
        handshakeInternal(exchange);
    }

    protected void handshakeInternal(final WebSocketHttpExchange exchange) {
        String origin = exchange.getRequestHeader(Headers.ORIGIN_STRING);
        if (origin != null) {
            exchange.setResponseHeader(Headers.ORIGIN_STRING, origin);
        }
        selectSubprotocol(exchange);
        selectExtensions(exchange);
        exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_LOCATION_STRING, getWebSocketLocation(exchange));

        final String key = exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_KEY_STRING);
        try {
            final String solution = solve(key);
            exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_ACCEPT_STRING, solution);
            performUpgrade(exchange);
        } catch (NoSuchAlgorithmException e) {
            exchange.endExchange();
            return;
        }
    }

    /**
     * convenience method to perform the upgrade
     */
    protected final void performUpgrade(final WebSocketHttpExchange exchange, final byte[] data) {
        exchange.setResponseHeader(Headers.CONTENT_LENGTH_STRING, String.valueOf(data.length));
        exchange.setResponseHeader(Headers.UPGRADE_STRING, "WebSocket");
        exchange.setResponseHeader(Headers.CONNECTION_STRING, "Upgrade");

        //

        upgradeChannel(exchange, data);
    }

    protected void upgradeChannel(final WebSocketHttpExchange exchange) {
        exchange.endExchange();
    }

    /**
     * Perform the upgrade using no payload
     */
    protected final void performUpgrade(final WebSocketHttpExchange exchange) {
        performUpgrade(exchange, EMPTY);
    }

    /**
     * Selects the first matching supported sub protocol and add it the the headers of the exchange.
     */
    protected final void selectSubprotocol(final WebSocketHttpExchange exchange) {
        String requestedSubprotocols = exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_PROTOCOL_STRING);
        if (requestedSubprotocols == null) {
            return;
        }

        String[] requestedSubprotocolArray = PATTERN.split(requestedSubprotocols);
        String subProtocol = supportedSubprotols(requestedSubprotocolArray);
        if (subProtocol != null && !subProtocol.isEmpty()) {
            exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_PROTOCOL_STRING, subProtocol);
        }

    }


    protected final void selectExtensions(final WebSocketHttpExchange exchange) {
        List<WebSocketExtensionData> requestedExtensions = WebSocketExtensionUtil.extractExtensions(exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING));
        List<WebSocketServerExtension> extensions = selectedExtension(requestedExtensions);
        if (extensions != null && !extensions.isEmpty()) {
            String headerValue = "";
            for (WebSocketServerExtension extension : extensions) {
                WebSocketExtensionData extensionData = extension.newReponseData();
                headerValue = appendExtension(headerValue,
                        extensionData.name(), extensionData.parameters());
            }
            exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING, headerValue);
        }

    }

    /**
     * Add a new WebSocket Extension handshake to the list of available extensions.
     *
     * @param extension a new {@code ExtensionHandshake}
     */
    public final void addExtension(WebSocketServerExtensionHandshaker extension) {
        availableExtensions.add(extension);
        allowExtensions = true;
    }

    /**
     * Create the {@code ExtensionFunction} list associated with the negotiated extensions defined in the exchange's response.
     *
     * @param exchange the exchange used to retrieve negotiated extensions
     * @return a list of {@code ExtensionFunction} with the implementation of the extensions
     */
    protected final List<WebSocketServerExtension> initExtensions(final WebSocketHttpExchange exchange) {
        String extHeader = exchange.getResponseHeaders().get(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING) != null ?
                exchange.getResponseHeaders().get(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING).get(0) : null;

        List<WebSocketServerExtension> ret = new ArrayList<>();
        if (extHeader != null) {
            List<WebSocketExtensionData> extensions = WebSocketExtensionUtil.extractExtensions(extHeader);
            if (extensions != null && !extensions.isEmpty()) {
                for (WebSocketExtensionData ext : extensions) {
                    for (WebSocketServerExtensionHandshaker extHandshake : availableExtensions) {
                        WebSocketServerExtension negotiated = extHandshake.handshakeExtension(ext);
                        if (negotiated != null) {
                            ret.add(negotiated);
                        }
                    }
                }
            }
        }
        return ret;
    }


    protected void upgradeChannel(final WebSocketHttpExchange exchange, byte[] data) {
        HandshakeUtil.prepareUpgrade(config.getEndpointConfiguration(), exchange);
        super.upgradeChannel(exchange, data);
    }

    public WebSocketChannel createChannel(WebSocketHttpExchange exchange, final StreamConnection c, final ByteBufferPool buffers) {
        WebSocketChannel channel = super.createChannel(exchange, c, buffers);
        HandshakeUtil.setConfig(channel, config);
        return channel;
    }

    protected String supportedSubprotols(String[] requestedSubprotocolArray) {
        return HandshakeUtil.selectSubProtocol(config, requestedSubprotocolArray);
    }

    protected List<WebSocketServerExtension> selectedExtension(List<WebSocketExtensionData> extensionList) {
        List<ExtensionImpl> ext = new ArrayList<>();
        for (WebSocketExtensionData i : extensionList) {
            ext.add(new ExtensionImpl(i));
        }
        List<Extension> selected = HandshakeUtil.selectExtensions(config, ext);
        if (selected == null) {
            return Collections.emptyList();
        }
        List<WebSocketServerExtension> ret = new ArrayList<>();
        for (Extension i : selected) {
            for (WebSocketServerExtensionHandshaker handshaker : availableExtensions) {
                WebSocketServerExtension negotiated = handshaker.handshakeExtension(((ExtensionImpl) i).getData());
                if (negotiated != null) {
                    ret.add(negotiated);
                }
            }
        }

        return ret;
    }


    static String appendExtension(String currentHeaderValue, String extensionName,
                                  Map<String, String> extensionParameters) {

        StringBuilder newHeaderValue = new StringBuilder(
                currentHeaderValue != null ? currentHeaderValue.length() : extensionName.length() + 1);
        if (currentHeaderValue != null && !currentHeaderValue.trim().isEmpty()) {
            newHeaderValue.append(currentHeaderValue);
            newHeaderValue.append(EXTENSION_SEPARATOR);
        }
        newHeaderValue.append(extensionName);
        for (Map.Entry<String, String> extensionParameter : extensionParameters.entrySet()) {
            newHeaderValue.append(PARAMETER_SEPARATOR);
            newHeaderValue.append(extensionParameter.getKey());
            if (extensionParameter.getValue() != null) {
                newHeaderValue.append(PARAMETER_EQUAL);
                newHeaderValue.append(extensionParameter.getValue());
            }
        }
        return newHeaderValue.toString();
    }

    public boolean matches(final WebSocketHttpExchange exchange) {
        if (exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_KEY_STRING) != null &&
                exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_VERSION_STRING) != null) {
            if (exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_VERSION_STRING)
                    .equals(WEB_SOCKET_VERSION)) {
                return HandshakeUtil.checkOrigin(config.getEndpointConfiguration(), exchange);
            }
        }
        return false;
    }

    protected final String solve(final String nonceBase64) throws NoSuchAlgorithmException {
        final String concat = nonceBase64.trim() + MAGIC_NUMBER;
        final MessageDigest digest = MessageDigest.getInstance(SHA1);
        digest.update(concat.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeBytes(digest.digest()).trim();
    }

}
