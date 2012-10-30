package io.undertow.websockets.server;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.websockets.WebSocketVersion;
import io.undertow.websockets.WebSocketVersionNotSupportedException;

public class WebSocketServerHandshakerFactory {
    private final String webSocketURL;

    private final String subprotocols;

    private final long maxFramePayloadLength;

    /**
     * Constructor
     *
     * @param subprotocols    CSV of supported protocols. Null if sub protocols not supported.
     * @param allowExtensions Allow extensions to be used in the reserved bits of the web socket frame
     */
    public WebSocketServerHandshakerFactory(String webSocketURL, String subprotocols) {
        this(webSocketURL, subprotocols, Long.MAX_VALUE);
    }

    /**
     * Constructor
     *
     * @param webSocketURL          URL for web socket communications. e.g "ws://myhost.com/mypath".
     *                              Subsequent web socket frames will be sent to this URL.
     * @param subprotocols          CSV of supported protocols. Null if sub protocols not supported.
     * @param maxFramePayloadLength Maximum allowable frame payload length. Setting this value to your application's
     *                              requirement may reduce denial of service attacks using long data frames.
     */
    public WebSocketServerHandshakerFactory(String webSocketURL, String subprotocols,
                                            long maxFramePayloadLength) {
        this.webSocketURL = webSocketURL;
        this.subprotocols = subprotocols;
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    /**
     * Return a {@link WebSocketServerHandshaker} for the given {@link HttpServerExchange}. If no {@link WebSocketServerHandshaker}
     * can be found for the requested WebSocketVersion, an {@link WebSocketVersionNotSupportedException} will get thrown.
     *
     * @param exchange The {@link HttpServerExchange} which will be used to determine which {@link WebSocketServerHandshaker} should get returned
     * @return handshaker
     *         The {@link WebSocketServerHandshaker} which can be used to handle the WebSocket upgrade
     * @throws WebSocketVersionNotSupportedException
     *          Will get thrown if the {@link HttpServerExchange} request and Upgrade to an unsupported WebSocket Version.
     */
    public WebSocketServerHandshaker getHandshaker(HttpServerExchange exchange) throws WebSocketVersionNotSupportedException {
        // Try to get the WebSocket Version to which an upgrade will be done
        String version = exchange.getRequestHeaders().getFirst(HttpString.tryFromString("Sec-WebSocket-Version"));
        if (version == null) {
            // if no "Sec-WebSocket-Version" header was present we just assume its Version 00
            return new WebSocket00ServerHandshaker(
                    webSocketURL, subprotocols, maxFramePayloadLength);
        } else {
            if (version.equals(WebSocketVersion.V13.toHttpHeaderValue())) {
                return new WebSocket13ServerHandshaker(webSocketURL, subprotocols, maxFramePayloadLength);
            } else if (version.equals(WebSocketVersion.V08.toHttpHeaderValue())) {
                // Version 8 of the wire protocol - version 10 of the draft hybi specification.
                // TODO: Support me
            }
        }
        // No handshaker found for the requested version
        throw new WebSocketVersionNotSupportedException();
    }
}
