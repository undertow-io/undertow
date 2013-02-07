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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Allows to send WebSocket PONG messages. PONG messages can be used to notify the remote peer that connection is still
 * in use and active. Anyway the prefered way for doing this is to send a PING message.
 *
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public interface PongFrameSender {

    /**
     * Send the a PONG websocket frame and notify the {@link SendCallback} once done.
     * It is possible to send multiple frames at the same time even if the {@link SendCallback} is not triggered yet.
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload
     *          The payload
     * @param callback
     *          The callback that is called when sending is done or {@code null} if no notification
     *          should be done.
     */
    void sendPong(ByteBuffer payload, SendCallback callback);

    /**
     * Send the a PING websocket frame and notify the {@link SendCallback} once done.
     * It is possible to send multiple frames at the same time even if the {@link SendCallback} is not triggered yet.
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload
     *          The payload
     * @param callback
     *          The callback that is called when sending is done or {@code null} if no notification
     *          should be done.
     */
    void sendPong(ByteBuffer[] payload, SendCallback callback);

    /**
     * Send the a PONG websocket frame and blocks until complete.
     * <p/>
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload
     *          The payload
     * @throws IOException
     *          If sending failed
     */
    void sendPong(ByteBuffer payload) throws IOException;

    /**
     * Send the a PONG websocket frame and blocks until complete.
     * <p/>
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload
     *          The payload
     * @throws IOException
     *          If sending failed
     */
    void sendPong(ByteBuffer[] payload) throws IOException;
}
