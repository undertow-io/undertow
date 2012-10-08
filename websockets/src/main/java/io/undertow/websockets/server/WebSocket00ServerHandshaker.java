package io.undertow.websockets.server;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import io.undertow.websockets.WebSocketHandshakeException;
import io.undertow.websockets.WebSocketReadListener;
import io.undertow.websockets.WebSocketVersion;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

/**
 * {@link WebSocketServerHandshaker} which can be used to issue the handshake for {@link WebSocketVersion#V00}.
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class WebSocket00ServerHandshaker extends WebSocketServerHandshaker {

    /**
     * Constructor using default values
     *
     * @param webSocketUrl
     *            URL for web socket communications. e.g
     *            "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols. Null if sub protocols not
     *            supported.
     */
    public WebSocket00ServerHandshaker(String webSocketUrl, String subprotocols) {
        super(WebSocketVersion.V00, webSocketUrl, subprotocols, Long.MAX_VALUE);
    }


    /**
     * Constructor specifying the destination web socket location
     * 
     * @param webSocketUrl
     *            URL for web socket communications. e.g
     *            "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols. Null if sub protocols not
     *            supported.
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     */
    public WebSocket00ServerHandshaker(String webSocketUrl, String subprotocols,
            long maxFramePayloadLength) {
        super(WebSocketVersion.V00, webSocketUrl, subprotocols, maxFramePayloadLength);
    }


    @Override
    protected WebSocketReadListener readListener(HttpServerExchange exchange) throws WebSocketHandshakeException {
        HeaderMap requestHeader = exchange.getRequestHeaders();
        // Serve the WebSocket handshake request.
        if (!"Upgrade".equalsIgnoreCase(requestHeader.getFirst(HttpString.tryFromString("Connection")))
                || !"WebSocket".equalsIgnoreCase(requestHeader.getFirst(HttpString.tryFromString("Upgrade")))) {
            throw new WebSocketHandshakeException("Not a WebSocket handshake request: missing upgrade in the headers");
        }

        // Hixie 75 does not contain these headers while Hixie 76 does
        boolean isHixie76 = requestHeader.contains(HttpString.tryFromString("Sec-WebSocket-Key1")) && requestHeader.contains(HttpString.tryFromString("Sec-WebSocket-Key2"));

        HeaderMap responseHeader = exchange.getResponseHeaders();
        responseHeader.add(HttpString.tryFromString("Upgrade"), "WebSocket");
        responseHeader.add(HttpString.tryFromString("Connection"), "Upgrade");

        // Fill in the headers and contents depending on handshake method.
        if (isHixie76) {
            // New handshake method with a challenge:
            responseHeader.add(HttpString.tryFromString("Sec-WebSocket-Origin"), requestHeader.getFirst(HttpString.tryFromString("Origin")));
            responseHeader.add(HttpString.tryFromString("Sec-WebSocket-Location"), getWebSocketUrl());
            String subprotocols = requestHeader.getFirst(HttpString.tryFromString("Sec-WebSocket-Protocol"));
            if (subprotocols != null) {
                String selectedSubprotocol = selectSubprotocol(subprotocols);
                responseHeader.add(HttpString.tryFromString("Sec-WebSocket-Origin"), selectedSubprotocol);
            }

            // Calculate the answer of the challenge from the request
            String key1 = requestHeader.getFirst(HttpString.tryFromString("Sec-WebSocket-Key1"));
            String key2 = requestHeader.getFirst(HttpString.tryFromString("Sec-WebSocket-Key2"));
            int a = (int) (Long.parseLong(key1.replaceAll("[^0-9]", "")) / key1.replaceAll("[^ ]", "").length());
            int b = (int) (Long.parseLong(key2.replaceAll("[^0-9]", "")) / key2.replaceAll("[^ ]", "").length());

            // allocate a ByteBuffer that can just hold the long that will be read out of the request payload
            ByteBuffer buf = ByteBuffer.allocate(8);
            StreamSourceChannel channel = exchange.getRequestChannel();
            try {
                int read = -1;
                int amount = 0;
                while((read = channel.read(buf)) != -1) {
                    amount =+ read;
                    if (amount >= 8) {
                        break;
                    }
                }
            } catch (IOException e) {
                throw new WebSocketHandshakeException("Uanble to read request payload", e);
            } finally {
                try {
                    channel.close();
                } catch (IOException e) {
                    // ignore on close
                }
            }
            
            buf.flip();
            
            long c = buf.getLong();
            ByteBuffer input = ByteBuffer.allocate(16);
            input.putInt(a);
            input.putInt(b);
            input.putLong(c);

            input.flip();

            ByteBuffer md5 = md5(input);

            // create a new Channel to write the response back to the client
            StreamSinkChannel ch = exchange.getResponseChannelFactory().create();
            try {
                while (md5.hasRemaining()) {
                    try {
                        ch.write(md5);
                    } catch (IOException e) {
                        throw new WebSocketHandshakeException("Uanble to write response payload", e);
                    }
                }
            } finally {
                try {
                    ch.close();
                } catch (IOException e) {
                    // ignore on close
                }
            }
            
        } else {
            // Old Hixie 75 handshake method has not challenge, so no need to generate one
            responseHeader.add(HttpString.tryFromString("WebSocket-Origin"), requestHeader.getFirst(HttpString.tryFromString("Origin")));
            responseHeader.add(HttpString.tryFromString("WebSocket-Location"), getWebSocketUrl());
            String protocol = requestHeader.getFirst(HttpString.tryFromString("WebSocket-Protocol"));
            if (protocol != null) {
                responseHeader.add(HttpString.tryFromString("WebSocket-Protocol"), selectSubprotocol(protocol));
            }
        }

        // TODO: Implement the listener
        return null;
    }
    
    /**
     * Generate the MD5 hash out of the given {@link ByteBuffer}
     *
     */
    private final static ByteBuffer md5(ByteBuffer buffer) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(buffer);
            return ByteBuffer.wrap(md.digest());
        } catch (NoSuchAlgorithmException e) {
            // Should never happen
            throw new InternalError("MD5 not supported on this platform");
        }
    }

}
