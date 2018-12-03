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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;
import io.undertow.util.Headers;
import io.undertow.websockets.jsr.WebSocketVersion;

/**
 * Abstract base class for doing a WebSocket Handshake.
 *
 * @author Mike Brock
 */
public abstract class Handshake {
    private final WebSocketVersion version;
    private final String hashAlgorithm;
    private final String magicNumber;
    protected final Set<String> subprotocols;
    private static final byte[] EMPTY = new byte[0];
    private static final Pattern PATTERN = Pattern.compile("\\s*,\\s*");
    protected Set<ExtensionHandshake> availableExtensions = new HashSet<>();
    protected boolean allowExtensions;

    protected Handshake(WebSocketVersion version, String hashAlgorithm, String magicNumber, final Set<String> subprotocols) {
        this.version = version;
        this.hashAlgorithm = hashAlgorithm;
        this.magicNumber = magicNumber;
        this.subprotocols = subprotocols;
    }

    /**
     * Return the version for which the {@link Handshake} can be used.
     */
    public WebSocketVersion getVersion() {
        return version;
    }

    /**
     * Return the algorithm that is used to hash during the handshake
     */
    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    /**
     * Return the magic number which will be mixed in
     */
    public String getMagicNumber() {
        return magicNumber;
    }

    /**
     * Return the full url of the websocket location of the given {@link WebSocketHttpExchange}
     */
    protected static String getWebSocketLocation(HttpServletRequest request, HttpServletResponse response) {
        String scheme;
        if ("https".equals(request.getScheme())) {
            scheme = "wss";
        } else {
            scheme = "ws";
        }
        return scheme + "://" + request.getHeader(Headers.HOST_STRING) + request.getRequestURI();
    }

    /**
     * Issue the WebSocket upgrade
     *
     * @param exchange The {@link WebSocketHttpExchange} for which the handshake and upgrade should occur.
     */
    public final void handshake(HttpServletRequest request, HttpServletResponse response) {
        handshakeInternal(request, response);
    }

    protected abstract void handshakeInternal(HttpServletRequest request, HttpServletResponse response);

    /**
     * Return {@code true} if this implementation can be used to issue a handshake.
     */
    public abstract boolean matches(HttpServletRequest request, HttpServletResponse response);

    /**
     * convenience method to perform the upgrade
     */
    protected final void performUpgrade(HttpServletRequest request, HttpServletResponse response, final byte[] data) {
        response.addHeader(Headers.CONTENT_LENGTH_STRING, String.valueOf(data.length));
        response.addHeader(Headers.UPGRADE_STRING, "WebSocket");
        response.addHeader(Headers.CONNECTION_STRING, "Upgrade");
        upgradeChannel(request, response, data);
    }

    protected void upgradeChannel(HttpServletRequest request, HttpServletResponse response, final byte[] data) {
        try {
            if (data.length > 0) {
                response.getOutputStream().write(data);
            }
            response.getOutputStream().close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Perform the upgrade using no payload
     */
    protected final void performUpgrade(HttpServletRequest request, HttpServletResponse response) {
        performUpgrade(request, response, EMPTY);
    }

    /**
     * Selects the first matching supported sub protocol and add it the the headers of the exchange.
     */
    protected final void selectSubprotocol(HttpServletRequest request, HttpServletResponse response) {
        String requestedSubprotocols = request.getHeader(Headers.SEC_WEB_SOCKET_PROTOCOL_STRING);
        if (requestedSubprotocols == null) {
            return;
        }

        String[] requestedSubprotocolArray = PATTERN.split(requestedSubprotocols);
        String subProtocol = supportedSubprotols(requestedSubprotocolArray);
        if (subProtocol != null && !subProtocol.isEmpty()) {
            response.setHeader(Headers.SEC_WEB_SOCKET_PROTOCOL_STRING, subProtocol);
        }

    }


    protected final void selectExtensions(HttpServletRequest request, HttpServletResponse response) {
        List<WebSocketExtensionData> requestedExtensions = WebSocketExtensionData.parse(request.getHeader(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING));
        List<WebSocketExtensionData> extensions = selectedExtension(requestedExtensions);
        if (extensions != null && !extensions.isEmpty()) {
            response.setHeader(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING, WebSocketExtensionData.toExtensionHeader(extensions));
        }

    }

    protected String supportedSubprotols(String[] requestedSubprotocolArray) {
        for (String p : requestedSubprotocolArray) {
            String requestedSubprotocol = p.trim();

            for (String supportedSubprotocol : subprotocols) {
                if (requestedSubprotocol.equals(supportedSubprotocol)) {
                    return supportedSubprotocol;
                }
            }
        }
        return null;
    }

    protected List<WebSocketExtensionData> selectedExtension(List<WebSocketExtensionData> extensionList) {
        List<WebSocketExtensionData> selected = new ArrayList<>();
        List<ExtensionHandshake> configured = new ArrayList<>();
        for (WebSocketExtensionData ext : extensionList) {
            for (ExtensionHandshake extHandshake : availableExtensions) {
                WebSocketExtensionData negotiated = extHandshake.accept(ext);
                if (negotiated != null && !extHandshake.isIncompatible(configured)) {
                    selected.add(negotiated);
                    configured.add(extHandshake);
                }
            }
        }
        return selected;
    }

    /**
     * Add a new WebSocket Extension handshake to the list of available extensions.
     *
     * @param extension a new {@code ExtensionHandshake}
     */
    public final void addExtension(ExtensionHandshake extension) {
        availableExtensions.add(extension);
        allowExtensions = true;
    }

    /**
     * Create the {@code ExtensionFunction} list associated with the negotiated extensions defined in the exchange's response.
     *
     * @param exchange the exchange used to retrieve negotiated extensions
     * @return a list of {@code ExtensionFunction} with the implementation of the extensions
     */
    protected final List<WebSocketExtension> initExtensions(final HttpServletResponse response) {
        String extHeader = response.getHeader(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING);

        List<WebSocketExtension> negotiated = new ArrayList<>();
        if (extHeader != null) {
            List<WebSocketExtensionData> extensions = WebSocketExtensionData.parse(extHeader);
            if (extensions != null && !extensions.isEmpty()) {
                for (WebSocketExtensionData ext : extensions) {
                    for (ExtensionHandshake extHandshake : availableExtensions) {
                        if (extHandshake.getName().equals(ext.getName())) {
                            negotiated.add(extHandshake.create());
                        }
                    }
                }
            }
        }
        return negotiated;
    }

    protected abstract WebSocketFrameDecoder newWebsocketDecoder();

    protected abstract WebSocketFrameEncoder newWebSocketEncoder();
}
