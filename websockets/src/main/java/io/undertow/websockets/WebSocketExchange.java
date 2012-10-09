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

package io.undertow.websockets;

import java.net.InetSocketAddress;

import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.util.AbstractAttachable;
import io.undertow.websockets.frame.WebSocketFrameType;
import io.undertow.websockets.server.WebSocketServerConnection;

/**
 * Exchange object for WebSockets
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class WebSocketExchange extends AbstractAttachable {

    private final StreamSinkChannel underlyingResponseChannel;
    private final StreamSourceChannel underlyingRequestChannel;
    private final WebSocketServerConnection connection;

    public WebSocketExchange(WebSocketServerConnection connection, final StreamSourceChannel requestChannel, final StreamSinkChannel responseChannel) {
        this.underlyingResponseChannel = responseChannel;
        this.underlyingRequestChannel = requestChannel;
        this.connection = connection;
    }

    public WebSocketFrameType getRequestFrameType() {
        return null;
    }

    /**
     * Get the underlying WS connection.
     *
     * @return the underlying WS connection
     */
    public WebSocketServerConnection getConnection() {
        return connection;
    }

    /**
     * Get the request URI scheme. Normally this is one of {@code ws} or {@code wss}.
     *
     * @return the request URI scheme
     */
    public String getRequestScheme() {
        if (getUrl().startsWith("wss:")) {
            return "wss";
        } else {
            return "ws";
        }
    }

    /**
     * Return <code>true</code> if this is handled via WebSocket Secure.
     */
    public boolean isSecure() {
        return getRequestScheme().equals("wss");
    }

    /**
     * Return the URL of the WebSocket endpoint.
     * 
     * @return url The URL of the endpoint
     */
    public String getUrl() {
        return connection.getWebSocketUrl();
    }

    /**
     * Return the {@link WebSocketVersion} which is used
     * 
     * @return version The {@link WebSocketVersion} which is in use
     */
    public WebSocketVersion getVersion() {
        return connection.getVersion();
    }

    /**
     * Get the source address of the HTTP request.
     *
     * @return the source address of the HTTP request
     */
    public InetSocketAddress getSourceAddress() {
        return connection.getPeerAddress(InetSocketAddress.class);
    }

    /**
     * Get the destination address of the HTTP request.
     *
     * @return the destination address of the HTTP request
     */
    public InetSocketAddress getDestinationAddress() {
        return connection.getLocalAddress(InetSocketAddress.class);
    }

    public StreamSourceChannel getRequestChannel() {
        return underlyingRequestChannel;
    }

    public StreamSinkChannel getResponseChannel() {
        return underlyingResponseChannel;
    }
}
