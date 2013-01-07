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

package io.undertow.websockets.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.regex.Pattern;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.Headers;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketHandshakeException;
import io.undertow.websockets.WebSocketMessages;
import io.undertow.websockets.WebSocketVersion;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.channels.StreamSinkChannel;

/**
 * Abstract base class for doing a WebSocket Handshake.
 *
 * @author Mike Brock
 */
public abstract class Handshake {
    private final WebSocketVersion version;
    private final String hashAlgorithm;
    private final String magicNumber;
    private final List<String> subprotocols;
    private static final byte[] EMPTY = new byte[0];
    private static final Pattern PATTERN = Pattern.compile(",");

    protected Handshake(WebSocketVersion version, String hashAlgorithm, String magicNumber, final List<String> subprotocols) {
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
     * @return
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
    protected static String getWebSocketLocation(HttpServerExchange exchange) {
        String scheme;
        if ("https".equals(exchange.getRequestScheme())) {
            scheme = "wss";
        } else {
            scheme = "ws";
        }
        return scheme + "://" + exchange.getRequestHeaders().getFirst(Headers.HOST) + exchange.getRequestURI();
    }

    /**
     * Issue the WebSocket upgrade
     *
     * @param exchange The {@link HttpServerExchange} for which the handshake and upgrade should occur.
     */
    public abstract IoFuture<WebSocketChannel> handshake(HttpServerExchange exchange);

    /**
     * Return {@code true} if this implementation can be used to issue a handshake.
     */
    public abstract boolean matches(HttpServerExchange exchange);

    /**
     * Create the {@link WebSocketChannel} from the {@link HttpServerExchange}
     */
    protected abstract WebSocketChannel createChannel(HttpServerExchange exchange);

    /**
     * convenience method to perform the upgrade
     */
    protected final void performUpgrade(final ConcreteIoFuture<WebSocketChannel> ioFuture, final HttpServerExchange exchange, final byte[] data) {
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(data.length));
        exchange.getResponseHeaders().put(Headers.UPGRADE, "WebSocket");
        exchange.getResponseHeaders().put(Headers.CONNECTION, "Upgrade");

        exchange.upgradeChannel();
        final StreamSinkChannel channel = exchange.getResponseChannelFactory().create();

        if(data.length > 0) {
            writePayload(ioFuture, exchange, channel, ByteBuffer.wrap(data));
        } else {
            try {
                flushAndCreateChannel(ioFuture, exchange, channel);
            } catch (WebSocketHandshakeException e) {
                ioFuture.setException(new IOException(e));
            }
        }
    }

    private void writePayload(final ConcreteIoFuture<WebSocketChannel> ioFuture,   final HttpServerExchange exchange, StreamSinkChannel channel, final ByteBuffer payload){
        while(payload.hasRemaining()) {
            try {
                int w = channel.write(payload);
                if (w == 0) {
                    channel.getWriteSetter().set(new ChannelListener<StreamSinkChannel>() {
                        @Override
                        public void handleEvent(StreamSinkChannel channel) {
                            writePayload(ioFuture, exchange, channel, payload);
                        }
                    });
                    channel.resumeWrites();

                    return;
                }

            } catch (IOException e) {
                ioFuture.setException(e);
                return;
            }
        }
        try {
            flushAndCreateChannel(ioFuture, exchange, channel);
        } catch (WebSocketHandshakeException e) {
            ioFuture.setException(new IOException(e));
        }

    }

    /**
     * Perform the upgrade using no payload
     */
    protected final IoFuture<WebSocketChannel> performUpgrade(final HttpServerExchange exchange) {
        final ConcreteIoFuture<WebSocketChannel> ioFuture = new ConcreteIoFuture<WebSocketChannel>();
        performUpgrade(ioFuture, exchange, EMPTY);
        return ioFuture;
    }

    private void flushAndCreateChannel(final ConcreteIoFuture<WebSocketChannel> ioFuture, final HttpServerExchange exchange, final StreamSinkChannel channel) throws WebSocketHandshakeException {
        try {
            channel.shutdownWrites();
            if (!channel.flush()) {
                final ChannelListener<StreamSinkChannel> listener = ChannelListeners
                        .flushingChannelListener(
                                new ChannelListener<StreamSinkChannel>() {
                                    @Override
                                    public void handleEvent(final StreamSinkChannel channel) {
                                        ioFuture.setResult(createChannel(exchange));
                                    }
                                }, new ChannelExceptionHandler<StreamSinkChannel>() {
                                    @Override
                                    public void handleException(final StreamSinkChannel channel, final IOException exception) {
                                        ioFuture.setException(exception);
                                    }
                                }
                        );
                channel.getWriteSetter().set(listener);
            } else {
                ioFuture.setResult(createChannel(exchange));
            }
        } catch (IOException e) {
            throw new WebSocketHandshakeException(e);
        }
    }

    /**
     * Selects the first matching supported sub protocol and add it the the headers of the exchange.
     *
     * @throws WebSocketHandshakeException Get thrown if no subprotocol could be found
     */
    protected final void selectSubprotocol(final HttpServerExchange exchange) throws WebSocketHandshakeException {
        String requestedSubprotocols = exchange.getRequestHeaders().getFirst(Headers.SEC_WEB_SOCKET_PROTOCOL);
        if (requestedSubprotocols == null || subprotocols.isEmpty()) {
            return;
        }

        String[] requestedSubprotocolArray = PATTERN.split(requestedSubprotocols);
        for (String p : requestedSubprotocolArray) {
            String requestedSubprotocol = p.trim();

            for (String supportedSubprotocol : subprotocols) {
                if (requestedSubprotocol.equals(supportedSubprotocol)) {
                    exchange.getResponseHeaders().put(Headers.SEC_WEB_SOCKET_PROTOCOL, supportedSubprotocol);
                    return;
                }
            }
        }
        // No match found
        throw WebSocketMessages.MESSAGES.unsupportedProtocol(requestedSubprotocols, subprotocols);
    }

}
