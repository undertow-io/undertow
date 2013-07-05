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

import io.undertow.websockets.api.FragmentedTextFrameSender;
import io.undertow.websockets.api.SendCallback;
import io.undertow.websockets.core.FragmentedMessageChannel;
import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.WebSocketMessages;
import org.xnio.channels.BlockingWritableByteChannel;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Default {@link FragmentedTextFrameSender} implementation which use a {@link io.undertow.websockets.core.WebSocketChannel} for the write
 * operations.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
final class DefaultFragmentedTextFrameSender extends DefaultTextFrameSender implements FragmentedTextFrameSender {

    private FragmentedMessageChannel channel;
    private boolean finalFragment;
    public DefaultFragmentedTextFrameSender(WebSocketChannelSession session){
        super(session);
    }

    @Override
    protected StreamSinkFrameChannel createSink(long payloadSize) throws IOException {
        if (session.closeFrameSent) {
            WebSocketMessages.MESSAGES.closeFrameSentBefore();
        }
        if (channel == null) {
            channel = session.getChannel().sendFragmentedText();
        }
        StreamSinkFrameChannel sink = channel.send(payloadSize, finalFragment);
        return sink;
    }

    @Override
    public void finalFragment() {
        finalFragment = true;
    }

    @Override
    public void sendText(final ByteBuffer payload, final SendCallback callback) {
        try {
            StreamSinkChannel sink = StreamSinkChannelUtils.applyAsyncSendTimeout(session, createSink(payload.remaining()));
            StreamSinkChannelUtils.send(sink, payload, callback);
        } catch (IOException e) {
            StreamSinkChannelUtils.safeNotify(callback, e);
        }
    }

    @Override
    public void sendText(final ByteBuffer[] payload, final SendCallback callback) {
        try {
            long length = StreamSinkChannelUtils.payloadLength(payload);
            StreamSinkChannel sink = StreamSinkChannelUtils.applyAsyncSendTimeout(session, createSink(length));
            StreamSinkChannelUtils.send(sink, payload, callback);
        } catch (IOException e) {
            StreamSinkChannelUtils.safeNotify(callback, e);
        }
    }
    @Override
    public void sendText(ByteBuffer payload) throws IOException {
        checkBlockingAllowed();

        StreamSinkChannel sink = createSink(payload.remaining());
        BlockingWritableByteChannel channel = new BlockingWritableByteChannel(sink);
        while(payload.hasRemaining()) {
            channel.write(payload);
        }
        sink.shutdownWrites();
        channel.flush();
        channel.close();
    }

    @Override
    public void sendText(ByteBuffer[] payload) throws IOException {
        checkBlockingAllowed();

        long length = StreamSinkChannelUtils.payloadLength(payload);
        StreamSinkChannel sink = createSink(length);
        BlockingWritableByteChannel channel = new BlockingWritableByteChannel(sink);
        long written = 0;
        while(written < length) {
            long w = channel.write(payload);
            if (w > 0) {
                written += w;
            }
        }
        sink.shutdownWrites();
        channel.flush();
        channel.close();
    }
}
