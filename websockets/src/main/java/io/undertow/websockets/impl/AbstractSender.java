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
package io.undertow.websockets.impl;

import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketMessages;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;

/**
 * Abstract base class for all senders.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
abstract class AbstractSender {
    protected final WebSocketChannelSession session;

    AbstractSender(WebSocketChannelSession session) {
        this.session = session;
    }

    /**
     * Create a new {@link StreamSinkChannel} which can be used to send a WebSocket frame.
     */
    protected StreamSinkChannel createSink(long payloadSize) throws IOException {
        if (session.closeFrameSent) {
            WebSocketMessages.MESSAGES.closeFrameSentBefore();
        }
        WebSocketFrameType type = type();
        if (type == WebSocketFrameType.CLOSE) {
            session.closeFrameSent = true;
        }
        return session.getChannel().send(type, payloadSize);
    }

    /**
     * Check if a blocking operation is allowed
     */
    protected final void checkBlockingAllowed() {
        if (session.executeInIoThread) {
            WebSocketMessages.MESSAGES.blockingOperationInIoThread();
        }
    }
    /**
     * The {@link WebSocketFrameType} for which a new {@link StreamSinkChannel} should be created via {@link #createSink(long)},
     */
    protected abstract WebSocketFrameType type();
}
