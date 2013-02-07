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
import io.undertow.websockets.core.WebSocketUtils;
import io.undertow.websockets.api.SendCallback;
import io.undertow.websockets.api.TextFrameSender;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;

/**
 * Default implementation of a {@link TextFrameSender}
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class DefaultTextFrameSender extends AbstractSender implements TextFrameSender {

    public DefaultTextFrameSender(WebSocketChannelSession session) {
        super(session);
    }

    @Override
    protected WebSocketFrameType type() {
        return WebSocketFrameType.TEXT;
    }

    @Override
    public void sendText(CharSequence payload, final SendCallback callback) {
        try {
            final ByteBuffer buffer = WebSocketUtils.fromUtf8String(payload);
            StreamSinkChannel sink = StreamSinkChannelUtils.applyAsyncSendTimeout(session, createSink(buffer.remaining()));
            StreamSinkChannelUtils.send(sink, buffer, callback);
        } catch (IOException e) {
            StreamSinkChannelUtils.safeNotify(callback, e);
        }
    }

    @Override
    public void sendText(CharSequence payload) throws IOException {
        checkBlockingAllowed();

        final ByteBuffer buffer = WebSocketUtils.fromUtf8String(payload);
        StreamSinkChannel sink = createSink(buffer.remaining());
        StreamSinkChannelUtils.send(sink, buffer);

    }

    @Override
    public Writer sendText(long payloadSize) throws IOException {
        checkBlockingAllowed();

        return new TextWriter(session.getChannel(), createSink(payloadSize));
    }
}
