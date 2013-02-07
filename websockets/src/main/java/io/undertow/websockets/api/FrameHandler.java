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
     *
     * @param session   the {@link WebSocketSession} for which the CLOSE frame was received.
     * @param reason    the {@link CloseReason} if any or {@code null} if none was send.
     */
    void onCloseFrame(WebSocketSession session, CloseReason reason);

    /**
     * Is called once a PING frame was received. Be aware that there is no need to echo back the frame as this is
     * done by the implementation as required by the RFC.
     *
     * @param session   the {@link WebSocketSession} for which the CLOSE frame was received.
     * @param payload   the actual payload which MUST at least contain one {@link ByteBuffer} which MAY be empty if
     *                  the frame did not contains any payload data.
     */
    void onPingFrame(WebSocketSession session, ByteBuffer... payload);

    /**
     * Is called once a PONG frame was received.
     *
     * @param session   the {@link WebSocketSession} for which the CLOSE frame was received.
     * @param payload   the actual payload which MUST at least contain one {@link ByteBuffer} which MAY be empty if
     *                  the frame did not contains any payload data.
     */
    void onPongFrame(WebSocketSession session, ByteBuffer... payload);

    /**
     * Is called if an error occurs while handling websocket frames. Once this message was called the implementation
     * will automatically drop the connection as there is no way to recover.
     *
     * @param session   the {@link WebSocketSession} for which the CLOSE frame was received.
     * @param cause     the {@link Throwable} which cases the error.
     */
    void onError(WebSocketSession session, Throwable cause);
}
