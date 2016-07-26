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

package io.undertow.websockets;


import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.HttpUpgradeListener;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.Methods;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.core.protocol.version07.Hybi07Handshake;
import io.undertow.websockets.core.protocol.version08.Hybi08Handshake;
import io.undertow.websockets.core.protocol.version13.Hybi13Handshake;
import io.undertow.websockets.extensions.ExtensionHandshake;
import io.undertow.websockets.spi.AsyncWebSocketHttpServerExchange;
import org.xnio.StreamConnection;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link HttpHandler} which will process the {@link HttpServerExchange} and do the actual handshake/upgrade
 * to WebSocket.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocketProtocolHandshakeHandler implements HttpHandler {
    private final Set<Handshake> handshakes;

    /**
     * The upgrade listener. This will only be used if another web socket implementation is being layered on top.
     */
    private final HttpUpgradeListener upgradeListener;

    private final WebSocketConnectionCallback callback;

    private final Set<WebSocketChannel> peerConnections = Collections.newSetFromMap(new ConcurrentHashMap<WebSocketChannel, Boolean>());

    /**
     * The handler that is invoked if there are no web socket headers
     */
    private final HttpHandler next;

    /**
     * Create a new {@link WebSocketProtocolHandshakeHandler}
     *
     * @param callback The {@link WebSocketConnectionCallback} which will be executed once the handshake was
     *                 established
     */
    public WebSocketProtocolHandshakeHandler(final WebSocketConnectionCallback callback) {
        this(callback, ResponseCodeHandler.HANDLE_404);
    }

    /**
     * Create a new {@link WebSocketProtocolHandshakeHandler}
     *
     * @param callback The {@link WebSocketConnectionCallback} which will be executed once the handshake was
     *                 established
     */
    public WebSocketProtocolHandshakeHandler(final WebSocketConnectionCallback callback, final HttpHandler next) {
        this.callback = callback;
        Set<Handshake> handshakes = new HashSet<>();
        handshakes.add(new Hybi13Handshake());
        handshakes.add(new Hybi08Handshake());
        handshakes.add(new Hybi07Handshake());
        this.handshakes = handshakes;
        this.next = next;
        this.upgradeListener = null;
    }

    /**
     * Create a new {@link WebSocketProtocolHandshakeHandler}
     *
     * @param handshakes The supported handshake methods
     * @param callback   The {@link WebSocketConnectionCallback} which will be executed once the handshake was
     *                   established
     */
    public WebSocketProtocolHandshakeHandler(Collection<Handshake> handshakes, final WebSocketConnectionCallback callback) {
        this(handshakes, callback, ResponseCodeHandler.HANDLE_404);
    }

    /**
     * Create a new {@link WebSocketProtocolHandshakeHandler}
     *
     * @param handshakes The supported handshake methods
     * @param callback   The {@link WebSocketConnectionCallback} which will be executed once the handshake was
     *                   established
     */
    public WebSocketProtocolHandshakeHandler(Collection<Handshake> handshakes, final WebSocketConnectionCallback callback, final HttpHandler next) {
        this.callback = callback;
        this.handshakes = new HashSet<>(handshakes);
        this.next = next;
        this.upgradeListener = null;
    }

    /**
     * Create a new {@link WebSocketProtocolHandshakeHandler}
     *
     * @param callback The {@link WebSocketConnectionCallback} which will be executed once the handshake was
     *                 established
     */
    public WebSocketProtocolHandshakeHandler(final HttpUpgradeListener callback) {
        this(callback, ResponseCodeHandler.HANDLE_404);
    }

    /**
     * Create a new {@link WebSocketProtocolHandshakeHandler}
     *
     * @param callback The {@link WebSocketConnectionCallback} which will be executed once the handshake was
     *                 established
     */
    public WebSocketProtocolHandshakeHandler(final HttpUpgradeListener callback, final HttpHandler next) {
        this.callback = null;
        Set<Handshake> handshakes = new HashSet<>();
        handshakes.add(new Hybi13Handshake());
        handshakes.add(new Hybi08Handshake());
        handshakes.add(new Hybi07Handshake());
        this.handshakes = handshakes;
        this.next = next;
        this.upgradeListener = callback;
    }


    /**
     * Create a new {@link WebSocketProtocolHandshakeHandler}
     *
     * @param handshakes The supported handshake methods
     * @param callback   The {@link WebSocketConnectionCallback} which will be executed once the handshake was
     *                   established
     */
    public WebSocketProtocolHandshakeHandler(Collection<Handshake> handshakes, final HttpUpgradeListener callback) {
        this(handshakes, callback, ResponseCodeHandler.HANDLE_404);
    }

    /**
     * Create a new {@link WebSocketProtocolHandshakeHandler}
     *
     * @param handshakes The supported handshake methods
     * @param callback   The {@link WebSocketConnectionCallback} which will be executed once the handshake was
     *                   established
     */
    public WebSocketProtocolHandshakeHandler(Collection<Handshake> handshakes, final HttpUpgradeListener callback, final HttpHandler next) {
        this.callback = null;
        this.handshakes = new HashSet<>(handshakes);
        this.next = next;
        this.upgradeListener = callback;
    }

    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        if (!exchange.getRequestMethod().equals(Methods.GET)) {
            // Only GET is supported to start the handshake
            next.handleRequest(exchange);
            return;
        }
        final AsyncWebSocketHttpServerExchange facade = new AsyncWebSocketHttpServerExchange(exchange, peerConnections);
        Handshake handshaker = null;
        for (Handshake method : handshakes) {
            if (method.matches(facade)) {
                handshaker = method;
                break;
            }
        }

        if (handshaker == null) {
            next.handleRequest(exchange);
        } else {
            WebSocketLogger.REQUEST_LOGGER.debugf("Attempting websocket handshake with %s on %s", handshaker, exchange);
            final Handshake selected = handshaker;
            if (upgradeListener == null) {
                exchange.upgradeChannel(new HttpUpgradeListener() {
                    @Override
                    public void handleUpgrade(StreamConnection streamConnection, HttpServerExchange exchange) {
                        WebSocketChannel channel = selected.createChannel(facade, streamConnection, facade.getBufferPool());
                        peerConnections.add(channel);
                        callback.onConnect(facade, channel);
                    }
                });
            } else {
                exchange.upgradeChannel(upgradeListener);
            }
            handshaker.handshake(facade);
        }
    }

    public Set<WebSocketChannel> getPeerConnections() {
        return peerConnections;
    }

    /**
     * Add a new WebSocket Extension into the handshakes defined in this handler.
     *
     * @param extension a new {@code ExtensionHandshake} instance
     * @return          current handler
     */
    public WebSocketProtocolHandshakeHandler addExtension(ExtensionHandshake extension) {
        if (extension != null) {
            for (Handshake handshake : handshakes) {
                handshake.addExtension(extension);
            }
        }
        return this;
    }
}
