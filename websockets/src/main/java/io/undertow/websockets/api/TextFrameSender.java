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
import java.io.Writer;

/**
 * Implementations can be used to send TEXT frames.
 *
 * @author Stuart Douglas
 */
public interface TextFrameSender {

    /**
     * Send the a text websocket frame and notify the {@link SendCallback} once done.
     * It is possible to send multiple frames at the same time even if the {@link SendCallback} is not triggered yet.
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload
     *          The payload which must be valid UTF8
     * @param callback
     *          The callback that is called when sending is done or {@code null} if no notification
     *          should be done.
     */
    void sendText(CharSequence payload, SendCallback callback);

    /**
     * Send the a text websocket frame and blocks until complete.
     * <p/>
     * The implementation is responsible to queue them up and send them in the correct order.
     *
     * @param payload
     *          The payload must be valid UTF8
     * @throws IOException
     *          If sending failed
     */
    void sendText(CharSequence payload) throws IOException;

    /**
     * Sends a text message using the resulting writer.
     * <p/>
     * This methods will block until the implementation is ready to actually send the message
     * (i.e. all previous messages in the queue have been sent).
     *
     * @param   payloadSize
     *          The payload size
     * @return  writer
     *          A writer that can be used to send a text message. the written content must
     *          be valid UTF8
     */
    Writer sendText(long payloadSize) throws IOException;
}
