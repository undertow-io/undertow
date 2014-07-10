/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.protocols.http2;

import java.nio.ByteBuffer;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Pooled;

import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.HeaderMap;
import io.undertow.util.ImmediatePooled;

/**
 * Headers channel
 *
 * @author Stuart Douglas
 */
public class Http2DataStreamSinkChannel extends Http2StreamSinkChannel {

    private final HeaderMap headers;

    private boolean first = true;
    private final HpackEncoder encoder;
    private ChannelListener<Http2DataStreamSinkChannel> completionListener;

    private final int frameType;

    Http2DataStreamSinkChannel(Http2Channel channel, int streamId, int frameType) {
        this(channel, streamId, new HeaderMap(), frameType);
    }

    Http2DataStreamSinkChannel(Http2Channel channel, int streamId, HeaderMap headers, int frameType) {
        super(channel, streamId);
        this.encoder = channel.getEncoder();
        this.headers = headers;
        this.frameType = frameType;
    }

    @Override
    protected SendFrameHeader createFrameHeaderImpl() {
        final int fcWindow = grabFlowControlBytes(getBuffer().remaining());
        if (fcWindow == 0 && getBuffer().hasRemaining()) {
            //flow control window is exhausted
            return new SendFrameHeader(getBuffer().remaining(), null);
        }
        final boolean finalFrame = isWritesShutdown() && fcWindow >= getBuffer().remaining();
        Pooled<ByteBuffer> firstHeaderBuffer = getChannel().getBufferPool().allocate();
        Pooled<ByteBuffer>[] allHeaderBuffers = null;
        ByteBuffer firstBuffer = firstHeaderBuffer.getResource();
        boolean firstFrame = false;
        if (first) {
            firstFrame = true;
            first = false;
            //back fill the length
            firstBuffer.put((byte) 0);
            firstBuffer.put((byte) 0);
            firstBuffer.put((byte) 0);

            firstBuffer.put((byte) frameType); //type
            firstBuffer.put((byte) ((isWritesShutdown() && !getBuffer().hasRemaining() ? Http2Channel.HEADERS_FLAG_END_STREAM : 0) | Http2Channel.HEADERS_FLAG_END_HEADERS)); //flags

            Http2ProtocolUtils.putInt(firstBuffer, getStreamId());

            HpackEncoder.State result = encoder.encode(headers, firstBuffer);
            Pooled<ByteBuffer> current = firstHeaderBuffer;
            int length = firstBuffer.position() - 9;
            while (result != HpackEncoder.State.COMPLETE) {
                //todo: add some kind of limit here
                allHeaderBuffers = allocateAll(allHeaderBuffers, current);
                current = allHeaderBuffers[allHeaderBuffers.length - 1];
                result = encoder.encode(headers, current.getResource());
                length += current.getResource().position();
            }
            firstBuffer.put(0, (byte) ((length >> 16) & 0xFF));
            firstBuffer.put(1, (byte) ((length >> 8) & 0xFF));
            firstBuffer.put(2, (byte) (length & 0xFF));
        }

        Pooled<ByteBuffer> currentPooled = allHeaderBuffers == null ? firstHeaderBuffer : allHeaderBuffers[allHeaderBuffers.length - 1];
        ByteBuffer currentBuffer = currentPooled.getResource();
        int remainingInBuffer = 0;
        if (getBuffer().remaining() > 0) {
            if (fcWindow > 0) {
                //make sure we have room in the header buffer
                if (currentBuffer.remaining() < 8) {
                    allHeaderBuffers = allocateAll(allHeaderBuffers, currentPooled);
                    currentPooled = allHeaderBuffers == null ? firstHeaderBuffer : allHeaderBuffers[allHeaderBuffers.length - 1];
                    currentBuffer = currentPooled.getResource();
                }
                remainingInBuffer = getBuffer().remaining() - fcWindow;
                getBuffer().limit(getBuffer().position() + fcWindow);

                currentBuffer.put((byte) ((fcWindow >> 16) & 0xFF));
                currentBuffer.put((byte) ((fcWindow >> 8) & 0xFF));
                currentBuffer.put((byte) (fcWindow & 0xFF));
                currentBuffer.put((byte) Http2Channel.FRAME_TYPE_DATA); //type
                currentBuffer.put((byte) (finalFrame ? Http2Channel.HEADERS_FLAG_END_STREAM : 0)); //flags
                Http2ProtocolUtils.putInt(currentBuffer, getStreamId());

            } else {
                remainingInBuffer = getBuffer().remaining();
            }
        } else if (finalFrame && !firstFrame) {
            currentBuffer.put((byte) 0);
            currentBuffer.put((byte) 0);
            currentBuffer.put((byte) 0);
            currentBuffer.put((byte) Http2Channel.FRAME_TYPE_DATA); //type
            currentBuffer.put((byte) (Http2Channel.HEADERS_FLAG_END_STREAM & 0xFF)); //flags
            Http2ProtocolUtils.putInt(currentBuffer, getStreamId());
        }
        if (allHeaderBuffers == null) {
            //only one buffer required
            currentBuffer.flip();
            return new SendFrameHeader(remainingInBuffer, currentPooled);
        } else {
            //headers were too big to fit in one buffer
            //for now we will just copy them into a big buffer
            int length = 0;
            for (int i = 0; i < allHeaderBuffers.length; ++i) {
                length += allHeaderBuffers[i].getResource().position();
                allHeaderBuffers[i].getResource().flip();
            }
            try {
                ByteBuffer newBuf = ByteBuffer.allocate(length);

                for (int i = 0; i < allHeaderBuffers.length; ++i) {
                    newBuf.put(allHeaderBuffers[i].getResource());
                }
                newBuf.flip();
                return new SendFrameHeader(remainingInBuffer, new ImmediatePooled<ByteBuffer>(newBuf));
            } finally {
                //the allocate can oome
                for (int i = 0; i < allHeaderBuffers.length; ++i) {
                    allHeaderBuffers[i].free();
                }
            }
        }

    }


    public HeaderMap getHeaders() {
        return headers;
    }

    @Override
    protected void handleFlushComplete(boolean finalFrame) {
        super.handleFlushComplete(finalFrame);
        if (finalFrame) {
            if (completionListener != null) {
                ChannelListeners.invokeChannelListener(this, completionListener);
            }
        }
    }

    public ChannelListener<Http2DataStreamSinkChannel> getCompletionListener() {
        return completionListener;
    }

    public void setCompletionListener(ChannelListener<Http2DataStreamSinkChannel> completionListener) {
        this.completionListener = completionListener;
    }
}
