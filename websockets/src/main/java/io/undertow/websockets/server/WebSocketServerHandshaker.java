/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.server;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.logging.Logger;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.PushBackStreamChannel;

import io.undertow.UndertowLogger;
import io.undertow.server.HttpServerConnection;
import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.WebSocketHandshakeException;
import io.undertow.websockets.WebSocketReadListener;
import io.undertow.websockets.WebSocketVersion;

/**
 * The {@link WebSocketServerHandshaker} is responsible to issue the WebSocket Handshake and upgrade via the {@link #handshake(HttpServerExchange)} method.
 * Once this method was called and successes without and {@link WebSocketHandshakeException} no futher {@link HttpServerExchange} will be generated in processed.
 * 
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public abstract class WebSocketServerHandshaker {

    protected final static UndertowLogger WEBSOCKET_LOGGER = Logger.getMessageLogger(UndertowLogger.class, UndertowLogger.class.getPackage().getName() + ".websocket");

    private final String url;

    private final Set<String> subprotocols;

    private final WebSocketVersion version;

    private final long maxFramePayloadLength;

    /**
     * {@link #WebSocketServerHandshaker(WebSocketVersion, String, String, long)} using {@link Long#MAX_VALUE} as 
     * maxFramePayloadLength.
     */
    protected WebSocketServerHandshaker(WebSocketVersion version, String webSocketUrl, String subprotocols) {
        this(version, webSocketUrl, subprotocols, Long.MAX_VALUE);
    }


    /**
     * Constructor a new {@link WebSocketServerHandshaker}
     *
     * @param version
     *            the protocol version for which this {@link WebSocketServerHandshaker} will be used
     * @param url
     *            URL for web socket communications. e.g
     *            "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subprotocols
     *            Comma-separated list of supported protocols. Null if sub protocols not
     *            supported.
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload.
     */
    protected WebSocketServerHandshaker(WebSocketVersion version, String url, String subprotocols,
            long maxFramePayloadLength) {
        this.version = version;
        this.url = url;
        if (subprotocols != null) {
            String[] subprotocolArray = subprotocols.split(",");
            for (int i = 0; i < subprotocolArray.length; i++) {
                subprotocolArray[i] = subprotocolArray[i].trim();
            }
            Set<String> subProtos = new HashSet<String>(subprotocolArray.length);
            Collections.addAll(subProtos, subprotocolArray);
            this.subprotocols = Collections.unmodifiableSet(subProtos);
        } else {
            this.subprotocols = Collections.<String>emptySet();
        }
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    /**
     * Returns the URL of the web socket
     */
    public final String getWebSocketUrl() {
        return url;
    }

    /**
     * Returns the comma-separated list of all supported sub protocols or <code>null</code>
     * if non are supported.
     */
    public final Set<String> getSubprotocols() {
        return subprotocols;
    }

    /**
     * Returns the version of the specification being supported
     */
    public WebSocketVersion getVersion() {
        return version;
    }

    /**
     * Returns the max length for any frame's payload
     */
    public long getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    
    /**
     * Selects the first matching supported sub protocol
     *
     * @param requestedSubprotocols
     *          Comma-separated list of protocols to be supported. e.g. "chat, superchat"
     * @return sub
     *          First matching supported sub protocol.
     * @throws WebSocketHandshakeException 
     *          Get thrown if no subprotocol could be found
     */
    protected final String selectSubprotocol(String requestedSubprotocols) throws WebSocketHandshakeException {
        if (requestedSubprotocols == null || subprotocols.isEmpty()) {
            return null;
        }

        String[] requestedSubprotocolArray = requestedSubprotocols.split(",");
        for (String p : requestedSubprotocolArray) {
            String requestedSubprotocol = p.trim();

            for (String supportedSubprotocol : subprotocols) {
                if (requestedSubprotocol.equals(supportedSubprotocol)) {
                    return requestedSubprotocol;
                }
            }
        }
        // No match found
        throw new WebSocketHandshakeException("Requested subprotocol(s) not supported: " + subprotocols);
    }

    /**
     * Issue the WebSocket upgrade and upgrade the {@link HttpServerConnection} to a {@link WebSocketServerConnection} once the
     * handshake was done.
     * 
     * @param exchange
     *          The {@link HttpServerExchange} for which the handshake and upgrade should occur.
     * @throws WebSocketHandshakeException
     *          Thrown if the handshake fails for what-ever reason.
     */
    public final void handshake(HttpServerExchange exchange) throws WebSocketHandshakeException {
        if (WEBSOCKET_LOGGER.isDebugEnabled()) {
            WEBSOCKET_LOGGER.debug("WS Version " + version.name() + " server handshake");
        }
        WebSocketReadListener listener = readListener(exchange);
        try {
            // Upgrade connection
            //
            // TODO: Review this later
            ConnectedStreamChannel channel = exchange.upgradeChannel();
            WebSocketServerConnection connection = new WebSocketServerConnection(version, getWebSocketUrl(), channel, exchange.getConnection().getBufferPool(), null, exchange.getConnection().getUndertowOptions());
            listener.setConnection(connection);

            final PushBackStreamChannel pushBackStreamChannel = new PushBackStreamChannel(channel);
            pushBackStreamChannel.getReadSetter().set(listener);
            listener.handleEvent(pushBackStreamChannel);
        } catch (Exception e) {
            throw new WebSocketHandshakeException("Error while perform the WebSocket Handshake", e);
        }

    }

    /**
     * Return the {@link WebSocketReadListener} for the given {@link HttpServerExchange}. Usually you will have one separate
     * {@link WebSocketReadListener} implementation per WebSocket Version.
     * 
     * @param exchange
     *          The {@link HttpServerExchange} which will be used parse all needed informations that are needed to generate the
     *          {@link WebSocketReadListener}.
     * @return listener
     *          The {@link WebSocketReadListener} for the given {@link HttpServerExchange}.
     * @throws WebSocketHandshakeException
     *          Get thrown if the {@link HttpServerExchange} does not contain all needed data to generate the {@link WebSocketReadListener}.
     */
    protected abstract WebSocketReadListener readListener(HttpServerExchange exchange) throws WebSocketHandshakeException;
}
