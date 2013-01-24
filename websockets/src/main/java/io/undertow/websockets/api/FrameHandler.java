/*
 * Copyright 2013 JBoss, by Red Hat, Inc
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
package io.undertow.websockets.api;


import java.nio.ByteBuffer;

/**
 * Handler to get notified once a WebSocket frame was received.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface FrameHandler {

    /**
     * Is called once a CLOSE frame was received. Be aware that there is no need to echo back the frame as this is
     * done by the implementation as required by the RFC.
     */
    void onCloseFrame(WebSocketSession session, CloseReason reason);

    /**
     * Is called once a PING frame was received. Be aware that there is no need to echo back the frame as this is
     * done by the implementation as required by the RFC.
     */
    void onPingFrame(WebSocketSession session, ByteBuffer... payload);

    /**
     * Is called once a PONG frame was received
     */
    void onPongFrame(WebSocketSession session, ByteBuffer... payload);

    /**
     * Is called if an error occurs while handling websocket frames. Once this message was called the implementation
     * will automatically drop the connection as there is no way to recover.
     */
    void onError(WebSocketSession session, Throwable cause);
}
