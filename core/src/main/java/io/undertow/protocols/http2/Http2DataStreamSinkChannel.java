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

import io.undertow.UndertowMessages;
import io.undertow.connector.PooledByteBuffer;
import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.HeaderMap;
import io.undertow.util.ImmediatePooledByteBuffer;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Headers channel
 *
 * @author Stuart Douglas
 */
public class Http2DataStreamSinkChannel extends Http2StreamSinkChannel implements Http2Stream {

    private final HeaderMap headers;

    private boolean first = true;
    private final HpackEncoder encoder;
    private volatile ChannelListener<Http2DataStreamSinkChannel> completionListener;

    private final int frameType;
    private boolean completionListenerReady;
    private volatile boolean completionListenerFailure; //true if the request is broken, and we should invoke the completion listener on the next user op
    private TrailersProducer trailersProducer;

    Http2DataStreamSinkChannel(Http2Channel channel, int streamId, int frameType) {
        this(channel, streamId, new HeaderMap(), frameType);
    }

    Http2DataStreamSinkChannel(Http2Channel channel, int streamId, HeaderMap headers, int frameType) {
        super(channel, streamId);
        this.encoder = channel.getEncoder();
        this.headers = headers;
        this.frameType = frameType;
    }

    public TrailersProducer getTrailersProducer() {
        return trailersProducer;
    }

    public void setTrailersProducer(TrailersProducer trailersProducer) {
        this.trailersProducer = trailersProducer;
    }

    @Override
    protected SendFrameHeader createFrameHeaderImpl() {
        //TODO: this is a mess WRT re-using between headers and push_promise, sort out a more reasonable abstraction
        int dataPaddingBytes = getChannel().getPaddingBytes();
        int attempted = getBuffer().remaining() + dataPaddingBytes + (dataPaddingBytes > 0 ? 1 : 0);
        final int fcWindow = grabFlowControlBytes(attempted);
        if (fcWindow == 0 && getBuffer().hasRemaining()) {
            //flow control window is exhausted
            return new SendFrameHeader(getBuffer().remaining(), null);
        }
        if(fcWindow <= dataPaddingBytes + 1) {
            //so we won't actually be able to send any data, just padding, which is obviously not what we want
            if(getBuffer().remaining() >= fcWindow) {
                //easy fix, we just don't send any padding
                dataPaddingBytes = 0;
            } else if (getBuffer().remaining() == dataPaddingBytes ){
                //corner case.
                dataPaddingBytes = 1;
            } else {
                dataPaddingBytes = fcWindow - getBuffer().remaining() - 1;
            }
        }

        final boolean finalFrame = isFinalFrameQueued() && fcWindow >= (getBuffer().remaining() + (dataPaddingBytes > 0 ? dataPaddingBytes + 1 : 0));
        PooledByteBuffer firstHeaderBuffer = getChannel().getBufferPool().allocate();
        PooledByteBuffer[] allHeaderBuffers = null;
        ByteBuffer firstBuffer = firstHeaderBuffer.getBuffer();
        boolean firstFrame = false;

        HeaderMap trailers = null;
        if(finalFrame && this.trailersProducer != null) {
            trailers = this.trailersProducer.getTrailers();
            if(trailers != null && trailers.size() == 0) {
                trailers = null;
            }
        }

        if (first) {
            firstFrame = true;
            first = false;
            //back fill the length
            firstBuffer.put((byte) 0);
            firstBuffer.put((byte) 0);
            firstBuffer.put((byte) 0);
            firstBuffer.put((byte) frameType); //type
            firstBuffer.put((byte) 0); //back fill the flags

            Http2ProtocolUtils.putInt(firstBuffer, getStreamId());

            int paddingBytes = getChannel().getPaddingBytes();
            if(paddingBytes > 0) {
                firstBuffer.put((byte) (paddingBytes & 0xFF));
            }
            writeBeforeHeaderBlock(firstBuffer);
            HeaderMap headers = this.headers;
            HpackEncoder.State result = encoder.encode(headers, firstBuffer);
            PooledByteBuffer current = firstHeaderBuffer;
            int headerFrameLength = firstBuffer.position() - 9 + paddingBytes;
            firstBuffer.put(0, (byte) ((headerFrameLength >> 16) & 0xFF));
            firstBuffer.put(1, (byte) ((headerFrameLength >> 8) & 0xFF));
            firstBuffer.put(2, (byte) (headerFrameLength & 0xFF));
            firstBuffer.put(4, (byte) ((isFinalFrameQueued() && !getBuffer().hasRemaining() && frameType == Http2Channel.FRAME_TYPE_HEADERS && trailers == null ? Http2Channel.HEADERS_FLAG_END_STREAM : 0) | (result == HpackEncoder.State.COMPLETE ? Http2Channel.HEADERS_FLAG_END_HEADERS : 0 ) | (paddingBytes > 0 ? Http2Channel.HEADERS_FLAG_PADDED : 0))); //flags
            ByteBuffer currentBuffer = firstBuffer;

            if(currentBuffer.remaining() < paddingBytes) {
                allHeaderBuffers = allocateAll(allHeaderBuffers, current);
                current = allHeaderBuffers[allHeaderBuffers.length - 1];
                currentBuffer = current.getBuffer();
            }
            for(int i = 0; i < paddingBytes; ++ i) {
                currentBuffer.put((byte) 0);
            }

            while (result != HpackEncoder.State.COMPLETE) {
                //todo: add some kind of limit here

                allHeaderBuffers = allocateAll(allHeaderBuffers, current);
                current = allHeaderBuffers[allHeaderBuffers.length - 1];
                result = encodeContinuationFrame(headers, current);

            }
        }

        PooledByteBuffer currentPooled = allHeaderBuffers == null ? firstHeaderBuffer : allHeaderBuffers[allHeaderBuffers.length - 1];
        ByteBuffer currentBuffer = currentPooled.getBuffer();
        ByteBuffer trailer = null;
        int remainingInBuffer = 0;
        boolean requiresTrailers = false;

        if (getBuffer().remaining() > 0) {
            if (fcWindow > 0) {
                //make sure we have room in the header buffer
                if (currentBuffer.remaining() < 10) {
                    allHeaderBuffers = allocateAll(allHeaderBuffers, currentPooled);
                    currentPooled = allHeaderBuffers == null ? firstHeaderBuffer : allHeaderBuffers[allHeaderBuffers.length - 1];
                    currentBuffer = currentPooled.getBuffer();
                }
                int toSend = fcWindow - dataPaddingBytes - (dataPaddingBytes > 0 ? 1 :0);
                remainingInBuffer = getBuffer().remaining() - toSend;

                getBuffer().limit(getBuffer().position() + toSend);

                currentBuffer.put((byte) ((fcWindow >> 16) & 0xFF));
                currentBuffer.put((byte) ((fcWindow >> 8) & 0xFF));
                currentBuffer.put((byte) (fcWindow & 0xFF));
                currentBuffer.put((byte) Http2Channel.FRAME_TYPE_DATA); //type
                if(trailers == null) {
                    currentBuffer.put((byte) ((finalFrame ? Http2Channel.DATA_FLAG_END_STREAM : 0) | (dataPaddingBytes > 0 ? Http2Channel.DATA_FLAG_PADDED : 0))); //flags
                } else {
                    if(finalFrame) {
                        requiresTrailers = true;
                    }
                    currentBuffer.put((byte) (dataPaddingBytes > 0 ? Http2Channel.DATA_FLAG_PADDED : 0)); //flags
                }
                Http2ProtocolUtils.putInt(currentBuffer, getStreamId());
                if(dataPaddingBytes > 0) {
                    currentBuffer.put((byte) (dataPaddingBytes & 0xFF));
                    trailer = ByteBuffer.allocate(dataPaddingBytes);
                }
            } else {
                remainingInBuffer = getBuffer().remaining();
            }
        } else if (finalFrame && !firstFrame) {
            currentBuffer.put((byte) ((fcWindow >> 16) & 0xFF));
            currentBuffer.put((byte) ((fcWindow >> 8) & 0xFF));
            currentBuffer.put((byte) (fcWindow & 0xFF));
            currentBuffer.put((byte) Http2Channel.FRAME_TYPE_DATA); //type
            if (trailers == null) {
                currentBuffer.put((byte) ((Http2Channel.HEADERS_FLAG_END_STREAM & 0xFF) | (dataPaddingBytes > 0 ? Http2Channel.DATA_FLAG_PADDED : 0))); //flags
            } else {
                requiresTrailers = true;
                currentBuffer.put((byte) (dataPaddingBytes > 0 ? Http2Channel.DATA_FLAG_PADDED : 0)); //flags
            }
            Http2ProtocolUtils.putInt(currentBuffer, getStreamId());
            if (dataPaddingBytes > 0) {
                currentBuffer.put((byte) (dataPaddingBytes & 0xFF));
                trailer = ByteBuffer.allocate(dataPaddingBytes);
            }
        } else if(finalFrame && trailers != null) {
            requiresTrailers = true;
        }

        if (requiresTrailers) {
            PooledByteBuffer firstTrailerBuffer = getChannel().getBufferPool().allocate();
            if (trailer != null) {
                firstTrailerBuffer.getBuffer().put(trailer);
            }
            firstTrailerBuffer.getBuffer().put((byte) 0);
            firstTrailerBuffer.getBuffer().put((byte) 0);
            firstTrailerBuffer.getBuffer().put((byte) 0);
            firstTrailerBuffer.getBuffer().put((byte) Http2Channel.FRAME_TYPE_HEADERS); //type
            firstTrailerBuffer.getBuffer().put((byte) (Http2Channel.HEADERS_FLAG_END_STREAM | Http2Channel.HEADERS_FLAG_END_HEADERS)); //back fill the flags

            Http2ProtocolUtils.putInt(firstTrailerBuffer.getBuffer(), getStreamId());
            HpackEncoder.State result = encoder.encode(trailers, firstTrailerBuffer.getBuffer());
            if (result != HpackEncoder.State.COMPLETE) {
                throw UndertowMessages.MESSAGES.http2TrailerToLargeForSingleBuffer();
            }
            int headerFrameLength = firstTrailerBuffer.getBuffer().position() - 9;
            firstTrailerBuffer.getBuffer().put(0, (byte) ((headerFrameLength >> 16) & 0xFF));
            firstTrailerBuffer.getBuffer().put(1, (byte) ((headerFrameLength >> 8) & 0xFF));
            firstTrailerBuffer.getBuffer().put(2, (byte) (headerFrameLength & 0xFF));
            firstTrailerBuffer.getBuffer().flip();
            int size = firstTrailerBuffer.getBuffer().remaining();
            trailer = ByteBuffer.allocate(size);
            trailer.put(firstTrailerBuffer.getBuffer());
            trailer.flip();
            firstTrailerBuffer.close();
        }
        if (allHeaderBuffers == null) {
            //only one buffer required
            currentBuffer.flip();
            return new SendFrameHeader(remainingInBuffer, currentPooled, false, trailer);
        } else {
            //headers were too big to fit in one buffer
            //for now we will just copy them into a big buffer
            int length = 0;
            for (int i = 0; i < allHeaderBuffers.length; ++i) {
                length += allHeaderBuffers[i].getBuffer().position();
                allHeaderBuffers[i].getBuffer().flip();
            }
            try {
                ByteBuffer newBuf = ByteBuffer.allocate(length);

                for (int i = 0; i < allHeaderBuffers.length; ++i) {
                    newBuf.put(allHeaderBuffers[i].getBuffer());
                }
                newBuf.flip();
                return new SendFrameHeader(remainingInBuffer, new ImmediatePooledByteBuffer(newBuf), false, trailer);
            } finally {
                //the allocate can oome
                for (int i = 0; i < allHeaderBuffers.length; ++i) {
                    allHeaderBuffers[i].close();
                }
            }
        }

    }

    private HpackEncoder.State encodeContinuationFrame(HeaderMap headers, PooledByteBuffer current) {
        ByteBuffer currentBuffer;
        HpackEncoder.State result;//continuation frame
        //note that if the buffers are small we may not actually need a continuation here
        //but it greatly reduces the code complexity
        //back fill the length
        currentBuffer = current.getBuffer();
        currentBuffer.put((byte) 0);
        currentBuffer.put((byte) 0);
        currentBuffer.put((byte) 0);
        currentBuffer.put((byte) Http2Channel.FRAME_TYPE_CONTINUATION); //type
        currentBuffer.put((byte) 0); //back fill the flags
        Http2ProtocolUtils.putInt(currentBuffer, getStreamId());
        result = encoder.encode(headers, currentBuffer);
        int contFrameLength = currentBuffer.position() - 9;
        currentBuffer.put(0, (byte) ((contFrameLength >> 16) & 0xFF));
        currentBuffer.put(1, (byte) ((contFrameLength >> 8) & 0xFF));
        currentBuffer.put(2, (byte) (contFrameLength & 0xFF));
        currentBuffer.put(4, (byte) (result == HpackEncoder.State.COMPLETE ? Http2Channel.HEADERS_FLAG_END_HEADERS : 0 )); //flags
        return result;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        handleFailedChannel();
        return super.write(srcs, offset, length);
    }

    private void handleFailedChannel() {
        if(completionListenerFailure && completionListener != null) {
            ChannelListeners.invokeChannelListener(this, completionListener);
            completionListener = null;
        }
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException {
        handleFailedChannel();
        return super.write(srcs);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        handleFailedChannel();
        return super.write(src);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        handleFailedChannel();
        return super.writeFinal(srcs, offset, length);
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs) throws IOException {
        handleFailedChannel();
        return super.writeFinal(srcs);
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        handleFailedChannel();
        return super.writeFinal(src);
    }

    @Override
    public boolean flush() throws IOException {
        handleFailedChannel();
        if(completionListenerReady && completionListener != null) {
            ChannelListeners.invokeChannelListener(this, completionListener);
            completionListener = null;
        }
        return super.flush();
    }

    protected void writeBeforeHeaderBlock(ByteBuffer buffer) {

    }

    protected boolean isFlushRequiredOnEmptyBuffer() {
        return first;
    }

    public HeaderMap getHeaders() {
        return headers;
    }

    @Override
    protected void handleFlushComplete(boolean finalFrame) {
        super.handleFlushComplete(finalFrame);
        if (finalFrame) {
            if (completionListener != null) {
                completionListenerReady = true;
            }
        }
    }

    @Override
    protected void channelForciblyClosed() throws IOException {
        super.channelForciblyClosed();
        if (completionListener != null) {
            completionListenerFailure = true;
        }
    }

    public ChannelListener<Http2DataStreamSinkChannel> getCompletionListener() {
        return completionListener;
    }

    public void setCompletionListener(ChannelListener<Http2DataStreamSinkChannel> completionListener) {
        this.completionListener = completionListener;
    }

    public interface TrailersProducer {
        HeaderMap getTrailers();
    }
}
