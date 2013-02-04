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

import io.undertow.websockets.api.PongFrameSender;
import io.undertow.websockets.api.SendCallback;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketFrameType;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DefaultPongFrameSender implements PongFrameSender {
    private final WebSocketChannelSession session;

    DefaultPongFrameSender(WebSocketChannelSession session) {
        this.session = session;
    }

    private StreamSinkFrameChannel createSink(long payloadSize) throws IOException {
        return session.getChannel().send(WebSocketFrameType.PONG, payloadSize);
    }

    @Override
    public void sendPong(final ByteBuffer payload, final SendCallback callback) {
        try {
            StreamSinkChannel sink = StreamSinkChannelUtils.applyAsyncSendTimeout(session, createSink(payload.remaining()));
            StreamSinkChannelUtils.send(sink, payload, callback);
        } catch (IOException e) {
            StreamSinkChannelUtils.safeNotify(callback, e);
        }
    }

    @Override
    public void sendPong(final ByteBuffer[] payload, final SendCallback callback) {
        try {
            final long length = StreamSinkChannelUtils.payloadLength(payload);
            StreamSinkChannel sink = StreamSinkChannelUtils.applyAsyncSendTimeout(session, createSink(length));
            StreamSinkChannelUtils.send(sink, payload, callback);
        } catch (IOException e) {
            StreamSinkChannelUtils.safeNotify(callback, e);
        }
    }

    @Override
    public void sendPong(ByteBuffer payload) throws IOException {
        StreamSinkChannel sink = createSink(payload.remaining());
        StreamSinkChannelUtils.send(sink, payload);
    }

    @Override
    public void sendPong(ByteBuffer[] payload) throws IOException {
        long length = StreamSinkChannelUtils.payloadLength(payload);
        StreamSinkChannel sink = createSink(length);
        StreamSinkChannelUtils.send(sink, payload);
    }
}
