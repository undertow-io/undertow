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
import java.util.List;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketHandshakeException;
import io.undertow.websockets.WebSocketMessages;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoFuture;
import org.xnio.channels.StreamSinkChannel;

/**
 * @author Mike Brock
 */
public abstract class Handshake {
    private final String version;
    private final String hashAlgorithm;
    private final String magicNumber;
    private final List<String> subprotocols;

    public Handshake(String version, String hashAlgorithm, String magicNumber, final List<String> subprotocols) {
        this.version = version;
        this.hashAlgorithm = hashAlgorithm;
        this.magicNumber = magicNumber;
        this.subprotocols = subprotocols;
    }

    public String getVersion() {
        return this.version;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public String getMagicNumber() {
        return magicNumber;
    }

    protected String getWebSocketLocation(HttpServerExchange exchange) {
        String scheme;
        if (exchange.getRequestScheme().equals("https")) {
            scheme = "wss";
        } else {
            scheme = "ws";
        }
        return scheme + "://" + exchange.getRequestHeaders().getFirst(Headers.HOST) + exchange.getRequestURI();
    }

    /**
     * Issue the WebSocket upgrade and upgrade the {@link io.undertow.server.HttpServerConnection} to a {@link WebSocketServerConnection} once the
     * handshake was done.
     *
     * @param exchange The {@link io.undertow.server.HttpServerExchange} for which the handshake and upgrade should occur.
     * @throws io.undertow.websockets.WebSocketHandshakeException
     *          Thrown if the handshake fails for what-ever reason.
     */
    public abstract IoFuture<WebSocketChannel> handshake(HttpServerExchange exchange) throws WebSocketHandshakeException;

    public abstract boolean matches(final HttpServerExchange exchange);

    protected abstract WebSocketChannel createChannel(HttpServerExchange exchange);

    /**
     * convenience method to perform the upgrade
     */
    protected void performUpgrade(final ConcreteIoFuture<WebSocketChannel> ioFuture, final HttpServerExchange exchange, final byte[] data) throws WebSocketHandshakeException {
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "" + data.length);
        exchange.upgradeChannel();
        final StreamSinkChannel channel = exchange.getResponseChannelFactory().create();

        if(data.length > 0) {

        }

        flushAndCreateChannel(ioFuture, exchange, channel);
    }

    protected IoFuture<WebSocketChannel> performUpgrade(final HttpServerExchange exchange) throws WebSocketHandshakeException {
        final ConcreteIoFuture<WebSocketChannel> ioFuture = new ConcreteIoFuture<WebSocketChannel>();
        performUpgrade(ioFuture, exchange, new byte[0]);
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
     * Selects the first matching supported sub protocol
     *
     * @param requestedSubprotocols Comma-separated list of protocols to be supported. e.g. "chat, superchat"
     * @return sub
     *         First matching supported sub protocol.
     * @throws WebSocketHandshakeException Get thrown if no subprotocol could be found
     */
    protected final void selectSubprotocol(final HttpServerExchange exchange) throws WebSocketHandshakeException {
        String requestedSubprotocols = exchange.getRequestHeaders().getFirst(Headers.SEC_WEB_SOCKET_PROTOCOL);
        if (requestedSubprotocols == null || subprotocols.isEmpty()) {
            return;
        }

        String[] requestedSubprotocolArray = requestedSubprotocols.split(",");
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
