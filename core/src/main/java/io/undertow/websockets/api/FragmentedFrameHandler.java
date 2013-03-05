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
 * {@link FrameHandler} which will allow to get notified once a WebSocket frame part is received.
 *
 * This should be used if your want to get notified about fragements of a Frame. When using WebSockets it is valid to
 * fragment a WebSocket Frame by sent the actual Frame (like TEXT or BINARY) and then keep sending CONTINUATION frames
 * until it is done. You can check if it is the last fragment of the Frame by check the {@link WebSocketFrameHeader#isLastFragement()}
 * method.
 * <p/>
 *
 * Be aware that it is not allowed by the spec to start to send i.e. a BINARY frame and start to send a TEXT frame
 * before the last fragment of the BINARY frame is received. So it is safe to assume that once i.e
 * {@link #onBinaryFrame(WebSocketSession, WebSocketFrameHeader, ByteBuffer...)} is called no call to
 * {@link #onTextFrame(WebSocketSession, WebSocketFrameHeader, ByteBuffer...)} will accour until {@link WebSocketFrameHeader#isLastFragement()}
 * returned {@code true}. PING / PONG / CLOSE frames are allowed in the middle.
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
