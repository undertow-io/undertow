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


import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.websockets.WebSocketHandshakeException;
import io.undertow.websockets.WebSocketVersion;
import io.undertow.websockets.WebSocketVersionNotSupportedException;

/**
 * {@link HttpHandler} which will process the {@link HttpServerExchange} and do the actual handshake/upgrade
 * to WebSocket.
 * 
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class WebSocketProtocolHandshakeHandler implements HttpHandler {
    private final String websocketPath;
    private final String subprotocols;

    /**
     * Create a new {@link WebSocketProtocolHandshakeHandler}
     * 
     * @param websocketPath     The path which is used to serve the WebSocket requests
     * @param subprotocols      The sub-protocols to handle
     */
    public WebSocketProtocolHandshakeHandler(String websocketPath, String subprotocols) {
        this.websocketPath = websocketPath;
        this.subprotocols = subprotocols;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, HttpCompletionHandler completionHandler) {
        if (!exchange.getRequestMethod().equals(Methods.GET)) {
            // Only GET is supported to start the handshake
            exchange.setResponseCode(403);
            completionHandler.handleComplete();
            return;
        }

        final WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                getWebSocketLocation(exchange, websocketPath), subprotocols);
        try {
            final WebSocketServerHandshaker handshaker = wsFactory.getHandshaker(exchange);
            handshaker.handshake(exchange);
            // After the handshake was complete we are now have the connection upgraded to WebSocket and no futher HTTP processing will take place.
        } catch (WebSocketVersionNotSupportedException e) {
            exchange.setResponseCode(101);
            exchange.getResponseHeaders().put(HttpString.tryFromString("Sec-WebSocket-Version"),  WebSocketVersion.V13.toHttpHeaderValue());
            completionHandler.handleComplete();
            return;
        } catch (WebSocketHandshakeException e) {
            exchange.setResponseCode(500);
            completionHandler.handleComplete();

            // TODO: Proper logging
            e.printStackTrace();
        }
        
    }
    
    /**
     * Get the proper WebSocket location
     * 
     * @param   exchange        The {@link HttpServerExchange} which is used to do the upgrade.
     * @param   path            The path which is used for serve WebSockets
     * @return  location        The complete location for WebSockets
     */
    private static final String getWebSocketLocation(HttpServerExchange exchange, String path) {
        String protocol = "ws";
        if (exchange.getRequestScheme().equalsIgnoreCase("https")) {
            // SSL in use so use Secure WebSockets
            protocol = "wss";
        }
        // TODO: Store the header names somewhere global and use these static fields to lookup.
        return protocol + "://" + exchange.getRequestHeaders().getLast(HttpString.tryFromString("Host")) + path;
    }

}
