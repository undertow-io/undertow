/*
 * Copyright 2012 JBoss, by Red Hat, Inc
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

package io.undertow.websockets.core.protocol;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.regex.Pattern;

import io.undertow.util.Headers;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketHandshakeException;
import io.undertow.websockets.core.WebSocketMessages;
import io.undertow.websockets.core.WebSocketVersion;
import io.undertow.websockets.core.handler.WebSocketConnectionCallback;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import io.undertow.websockets.spi.UpgradeCallback;
import org.xnio.IoFuture;
import org.xnio.Pool;
import org.xnio.StreamConnection;
import org.xnio.channels.AssembledConnectedStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;

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
    private static final Pattern PATTERN = Pattern.compile(",");

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
     * Return the full url of the websocket location of the given {@link HttpServerExchange}
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
     * @param exchange The {@link HttpServerExchange} for which the handshake and upgrade should occur.
     * @param callback The callback to call once the exchange is upgraded
     */
    public final void handshake(final WebSocketHttpExchange exchange, final WebSocketConnectionCallback callback) {

        exchange.upgradeChannel(new UpgradeCallback() {

            @Override
            public void handleUpgrade(final StreamConnection channel, final Pool<ByteBuffer> buffers) {
                //TODO: fix this up to use the new API and not assembled
                WebSocketChannel webSocket = createChannel(exchange, new AssembledConnectedStreamChannel(channel, channel.getSourceChannel(), channel.getSinkChannel()), buffers);
                callback.onConnect(exchange, webSocket);
            }
        });
        handshakeInternal(exchange);
    }

    protected abstract void handshakeInternal(final WebSocketHttpExchange exchange);

    /**
     * Return {@code true} if this implementation can be used to issue a handshake.
     */
    public abstract boolean matches(WebSocketHttpExchange exchange);

    /**
     * Create the {@link WebSocketChannel} from the {@link HttpServerExchange}
     */
    public abstract WebSocketChannel createChannel(WebSocketHttpExchange exchange, final ConnectedStreamChannel channel, final Pool<ByteBuffer> pool);

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
                if(ioFuture.getStatus() == IoFuture.Status.DONE) {
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
     * @throws WebSocketHandshakeException Get thrown if no subprotocol could be found
     */
    protected final void selectSubprotocol(final WebSocketHttpExchange exchange) throws WebSocketHandshakeException {
        String requestedSubprotocols = exchange.getRequestHeader(Headers.SEC_WEB_SOCKET_PROTOCOL_STRING);
        if (requestedSubprotocols == null) {
            return;
        }

        String[] requestedSubprotocolArray = PATTERN.split(requestedSubprotocols);
        String subProtocol = supportedSubprotols(requestedSubprotocolArray);
        if (subProtocol == null) {
            // No match found
            throw WebSocketMessages.MESSAGES.unsupportedProtocol(requestedSubprotocols, subprotocols);
        }
        exchange.setResponseHeader(Headers.SEC_WEB_SOCKET_PROTOCOL_STRING, subProtocol);

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
}
