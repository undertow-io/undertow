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

package io.undertow.websockets.core.protocol;

import io.undertow.util.Headers;
import io.undertow.websockets.WebSocketExtension;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.extensions.ExtensionFunction;
import io.undertow.websockets.extensions.ExtensionHandshake;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.xnio.IoFuture;
import io.undertow.connector.ByteBufferPool;
import org.xnio.StreamConnection;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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
        exchange.putAttachment(WebSocketVersion.ATTACHMENT_KEY, version);
        handshakeInternal(exchange);
    }

    protected abstract void handshakeInternal(final WebSocketHttpExchange exchange);

    /**
     * Return {@code true} if this implementation can be used to issue a handshake.
     */
    public abstract boolean matches(WebSocketHttpExchange exchange);

    /**
     * Create the {@link WebSocketChannel} from the {@link WebSocketHttpExchange}
     */
    public abstract WebSocketChannel createChannel(WebSocketHttpExchange exchange, final StreamConnection channel, final ByteBufferPool pool);

    /**
     * convenience method to perform the upgrade
     */
    protected final void performUpgrade(final WebSocketHttpExchange exchange, final byte[] data) {
        exchange.setResponseHeader(Headers.CONTENT_LENGTH_STRING, String.valueOf(data.length));
        exchange.setResponseHeader(Headers.UPGRADE_STRING, "WebSocket");
        exchange.setResponseHeader(Headers.CONNECTION_STRING, "Upgrade");
        upgradeChannel(exchange, data);
    }

    protected void upgradeChannel(final WebSocketHttpExchange exchange, final byte[] data) {
        if (data.length > 0) {
            writePayload(exchange, ByteBuffer.wrap(data));
        } else {
            exchange.endExchange();
        }
    }

    private static void writePayload(final WebSocketHttpExchange exchange, final ByteBuffer payload) {
        exchange.sendData(payload).addNotifier(new IoFuture.Notifier<Void, Object>() {
            @Override
            public void notify(final IoFuture<? extends Void> ioFuture, final Object attachment) {
                if (ioFuture.getStatus() == IoFuture.Status.DONE) {
                    exchange.endExchange();
                } else {
                    exchange.close();
                }
            }
        }, null);
    }

    /**
     * Perform the upgrade using no payload
     */
    protected final void performUpgrade(final WebSocketHttpExchange exchange) {
        performUpgrade(exchange, EMPTY);
    }

    /**
     * Selects the first matching supported sub protocol and add it the the headers of the exchange.
     *
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
        List<WebSocketExtension> requestedExtensions = WebSocketExtension.parse(exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING));
        List<WebSocketExtension> extensions = selectedExtension(requestedExtensions);
        if (extensions != null && !extensions.isEmpty()) {
            exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING, WebSocketExtension.toExtensionHeader(extensions));
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

    protected List<WebSocketExtension> selectedExtension(List<WebSocketExtension> extensionList) {
        List<WebSocketExtension> selected = new ArrayList<>();
        List<ExtensionHandshake> configured = new ArrayList<>();
        for (WebSocketExtension ext : extensionList) {
            for (ExtensionHandshake extHandshake : availableExtensions) {
                WebSocketExtension negotiated = extHandshake.accept(ext);
                if (ext != null && !extHandshake.isIncompatible(configured)) {
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
     * @return         a list of {@code ExtensionFunction} with the implementation of the extensions
     */
    protected final List<ExtensionFunction> initExtensions(final WebSocketHttpExchange exchange) {
        String extHeader = exchange.getResponseHeaders().get(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING) != null ?
                exchange.getResponseHeaders().get(Headers.SEC_WEB_SOCKET_EXTENSIONS_STRING).get(0) : null;

        List<ExtensionFunction> negotiated = new ArrayList<>();
        if (extHeader != null) {
            List<WebSocketExtension> extensions = WebSocketExtension.parse(extHeader);
            if (extensions != null && !extensions.isEmpty()) {
                for (WebSocketExtension ext : extensions) {
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
}
