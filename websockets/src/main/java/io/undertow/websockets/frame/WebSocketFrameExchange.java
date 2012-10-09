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

package io.undertow.websockets.frame;

import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import io.undertow.websockets.WebSocketExchange;
import io.undertow.websockets.server.WebSocketServerConnection;

/**
 * Exchange object for WebSockets which holds the complete parsed {@link WebSocketFrame} and also offers methods
 * to "easily" send {@link WebSocketFrame}'s over the WebSocket connection.
 * 
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 *
 */
public class WebSocketFrameExchange extends WebSocketExchange {

    private final WebSocketFrame frame;

    public WebSocketFrameExchange(WebSocketServerConnection connection, WebSocketFrame frame, StreamSourceChannel requestChannel,
            StreamSinkChannel responseChannel) {
        super(connection, requestChannel, responseChannel);
        this.frame = frame;
    }

    
    @Override
    public WebSocketFrameType getRequestFrameType() {
        return frame.getType();
    }

    /**
     * Return the {@link WebSocketFrame} of the request.
     * 
     */
    public WebSocketFrame getRequestFrame() {
        return frame;
    }

    // TODO: Add methods to easy send a WebSocketFrame without the need to handle it directly via Stream
}
