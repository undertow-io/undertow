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

import io.undertow.websockets.api.CloseFrameSender;
import io.undertow.websockets.api.CloseReason;
import io.undertow.websockets.api.SendCallback;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import io.undertow.websockets.core.WebSocketUtils;
import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DefaultCloseFrameSender implements CloseFrameSender {
    private final WebSocketChannelSession session;
    private final SendCallback closeCallback = new SendCallback() {
        @Override
        public void onCompletion() {
            IoUtils.safeClose(session.getChannel());
        }

        @Override
        public void onError(Throwable cause) {
            IoUtils.safeClose(session.getChannel());
        }
    };

    DefaultCloseFrameSender(WebSocketChannelSession session) {
        this.session = session;
    }

    private StreamSinkFrameChannel createSink(long payloadSize) throws IOException {
        return session.getChannel().send(WebSocketFrameType.CLOSE, payloadSize);
    }

    @Override
    public void sendClose(CloseReason reason, SendCallback callback) {
        Pooled<ByteBuffer> pooled = closeFrame(reason);
        boolean free = true;
        try {
            ByteBuffer payload = pooled.getResource();
            StreamSinkChannel sink = StreamSinkChannelUtils.applyAsyncSendTimeout(session, createSink(payload.remaining()));
            if (callback == null) {
                callback = new SendCallbacks(new PooledFreeupSendCallback(pooled), closeCallback);
            } else {
                callback = new SendCallbacks(callback, new PooledFreeupSendCallback(pooled), closeCallback);
            }
            StreamSinkChannelUtils.send(sink, payload, callback);
            free = false;
        } catch (IOException e) {
            StreamSinkChannelUtils.safeNotify(callback, e);
            IoUtils.safeClose(session.getChannel());
        } finally {
            if (free) {
                pooled.free();
            }
        }
    }

    @Override
    public void sendClose(CloseReason reason) throws IOException {
        Pooled<ByteBuffer> pooled = closeFrame(reason);
        try {
            ByteBuffer payload = pooled.getResource();
            StreamSinkChannel sink = createSink(payload.remaining());
            StreamSinkChannelUtils.send(sink, payload);
        } finally {
            IoUtils.safeClose(session.getChannel());
            pooled.free();
        }
    }

    private Pooled<ByteBuffer> closeFrame(CloseReason reason) {
        if (reason == null) {
            return Buffers.EMPTY_POOLED_BYTE_BUFFER;
        }
        final Pooled<ByteBuffer> pooled = session.getChannel().getBufferPool().allocate();
        ByteBuffer buffer = pooled.getResource();
        buffer.putShort((short) reason.getStatusCode());
        String reasonText = reason.getReasonText();
        if (reasonText != null) {
            buffer.put(reasonText.getBytes(WebSocketUtils.UTF_8));
        }
        buffer.flip();
        return pooled;
    }
}
