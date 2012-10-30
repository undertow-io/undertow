package io.undertow.websockets.server;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.websockets.WebSocketChannel;
import io.undertow.websockets.WebSocketHandshakeException;
import io.undertow.websockets.WebSocketMessages;
import io.undertow.websockets.WebSocketVersion;
import io.undertow.websockets.version13.WebSocket13Channel;
import org.xnio.IoFuture;

/**
 * {@link io.undertow.websockets.server.WebSocketServerHandshaker} which can be used to issue the handshake for {@link io.undertow.websockets.WebSocketVersion#V00}.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocket13ServerHandshaker extends WebSocketServerHandshaker {

    public static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /**
     * Constructor using default values
     *
     * @param webSocketUrl URL for web socket communications. e.g
     *                     "ws://myhost.com/mypath". Subsequent web socket frames will be
     *                     sent to this URL.
     * @param subprotocols CSV of supported protocols. Null if sub protocols not
     *                     supported.
     */
    public WebSocket13ServerHandshaker(String webSocketUrl, String subprotocols) {
        super(WebSocketVersion.V13, webSocketUrl, subprotocols, Long.MAX_VALUE);
    }


    /**
     * Constructor specifying the destination web socket location
     *
     * @param webSocketUrl          URL for web socket communications. e.g
     *                              "ws://myhost.com/mypath". Subsequent web socket frames will be
     *                              sent to this URL.
     * @param subprotocols          CSV of supported protocols. Null if sub protocols not
     *                              supported.
     * @param maxFramePayloadLength Maximum length of a frame's payload
     */
    public WebSocket13ServerHandshaker(String webSocketUrl, String subprotocols,
                                       long maxFramePayloadLength) {
        super(WebSocketVersion.V13, webSocketUrl, subprotocols, maxFramePayloadLength);
    }


    @Override
    public IoFuture<WebSocketChannel> handshake(final HttpServerExchange exchange) throws WebSocketHandshakeException {
        HeaderMap requestHeader = exchange.getRequestHeaders();
        // Serve the WebSocket handshake request.
        if (!"Upgrade".equalsIgnoreCase(requestHeader.getFirst(Headers.CONNECTION))) {
            throw WebSocketMessages.MESSAGES.missingHeader(Headers.CONNECTION_STRING);
        } else if (!"WebSocket".equalsIgnoreCase(requestHeader.getFirst(Headers.UPGRADE))) {
            throw WebSocketMessages.MESSAGES.missingHeader(Headers.UPGRADE_STRING);
        }

        HeaderMap responseHeader = exchange.getResponseHeaders();
        responseHeader.add(Headers.UPGRADE, "WebSocket");
        responseHeader.add(Headers.CONNECTION, "Upgrade");

        // New handshake method with a challenge:

        String subprotocols = requestHeader.getFirst(HttpString.tryFromString("Sec-WebSocket-Protocol"));
        if (subprotocols != null) {
            String selectedSubprotocol = selectSubprotocol(subprotocols);
            responseHeader.add(HttpString.tryFromString("Sec-WebSocket-Protocol"), selectedSubprotocol);
        }

        // Calculate the answer of the challenge from the request
        String key = requestHeader.getFirst(HttpString.tryFromString("Sec-WebSocket-Key"));
        if (key == null) {
            throw WebSocketMessages.MESSAGES.missingHeader("Sec-WebSocket-Key");
        }
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new WebSocketHandshakeException(e);
        }

        String result;
        try {
            final byte[] input = (key + GUID).getBytes("ASCII");
            result = Base64.encodeBytes(md.digest(input));
        } catch (UnsupportedEncodingException e) {
            throw new WebSocketHandshakeException(e);
        }
        responseHeader.put(HttpString.tryFromString("Sec-WebSocket-Accept"), result);

        //now we are ready to send back our response
        responseHeader.put(Headers.CONTENT_LENGTH, "0");
        return performUpgrade(exchange);
    }

    @Override
    protected WebSocketChannel createChannel(final HttpServerExchange exchange) {
        return new WebSocket13Channel(exchange.getConnection().getChannel(), exchange.getConnection().getBufferPool(), getWebSocketUrl());
    }

}
