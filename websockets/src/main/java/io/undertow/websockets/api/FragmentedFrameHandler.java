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
 * {@link FrameHandler} which will allow to get notified once a WebSocket frame parts are received.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface FragmentedFrameHandler extends FrameHandler {

    /**
     * Is called once a complete TEXT frame was received, so the server is responsible to buffer the whole payload
     * until then.
     *
     * @param session   the {@link WebSocketSession} for which a binary frame was received
     * @param header    the {@link WebSocketFrameHeader  which belongs to the frame.
     * @param payload   the actual payload which MUST at least contain one {@link ByteBuffer} which MAY be empty if
     *                  the frame did not contains any payload data.
     */
    void onTextFrame(WebSocketSession session, WebSocketFrameHeader header, ByteBuffer... payload);

    /**
     * Is called once a complete BINARY frame was received, so the server is responsible to buffer the whole payload
     * until then. Be aware that the payload by be broken down in more then one {@link ByteBuffer} to allow the
     * implementation to make use of more performant allocation and reuse.
     *
     * Once this methods returns the implementation may reuse or clear the {@link ByteBuffer}s, so the user is
     * responsible to make a copy of it if the payload is needed later.
     *
     *
     * @param session   the {@link WebSocketSession} for which a binary frame was received
     * @param header    the {@link WebSocketFrameHeader  which belongs to the frame.
     * @param payload   the actual payload which MUST at least contain one {@link ByteBuffer} which MAY be empty if
     *                  the frame did not contains any payload data.
     */
    void onBinaryFrame(WebSocketSession session, WebSocketFrameHeader header, ByteBuffer... payload);
}
