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

package io.undertow.protocols.spdy;

import io.undertow.UndertowMessages;
import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;

import org.xnio.IoUtils;
import io.undertow.connector.PooledByteBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;

/**
 * @author Stuart Douglas
 */
public abstract class SpdyStreamStreamSinkChannel extends SpdyStreamSinkChannel {

    private final int streamId;
    private volatile boolean reset = false;

    //flow control related items. Accessed under lock
    private int flowControlWindow;
    private int initialWindowSize; //we track the initial window size, and then re-query it to get any delta

    private SendFrameHeader header;

    SpdyStreamStreamSinkChannel(SpdyChannel channel, int streamId) {
        super(channel);
        this.streamId = streamId;
        this.flowControlWindow = channel.getInitialWindowSize();
        this.initialWindowSize = this.flowControlWindow;
    }

    public int getStreamId() {
        return streamId;
    }

    SendFrameHeader generateSendFrameHeader() {
        header = createFrameHeaderImpl();
        return header;
    }

    void clearHeader() {
        this.header = null;
    }

    @Override
    protected void channelForciblyClosed() throws IOException {
        getChannel().removeStreamSink(getStreamId());
        if(reset) {
            return;
        }
        reset = true;
        if (streamId % 2 == (getChannel().isClient() ? 1 : 0)) {
            //we initiated the stream
            //we only actually reset if we have sent something to the other endpoint
            if(isFirstDataWritten()) {
                getChannel().sendRstStream(streamId, SpdyChannel.RST_STATUS_CANCEL);
            }
        } else {
            getChannel().sendRstStream(streamId, SpdyChannel.RST_STATUS_INTERNAL_ERROR);
        }
        markBroken();
    }

    @Override
    protected final SendFrameHeader createFrameHeader() {
        SendFrameHeader header = this.header;
        this.header = null;
        return header;
    }

    @Override
    protected void handleFlushComplete(boolean finalFrame) {
        if(finalFrame) {
            getChannel().removeStreamSink(getStreamId());
        }
    }

    protected PooledByteBuffer[] createHeaderBlock(PooledByteBuffer firstHeaderBuffer, PooledByteBuffer[] allHeaderBuffers, ByteBuffer firstBuffer, HeaderMap headers, boolean unidirectional) {
        PooledByteBuffer outPooled = getChannel().getHeapBufferPool().allocate();
        PooledByteBuffer inPooled = getChannel().getHeapBufferPool().allocate();
        try {

            PooledByteBuffer currentPooled = firstHeaderBuffer;
            ByteBuffer inputBuffer = inPooled.getBuffer();
            ByteBuffer outputBuffer = outPooled.getBuffer();

            SpdyProtocolUtils.putInt(inputBuffer, headers.size());

            long fiCookie = headers.fastIterateNonEmpty();
            while (fiCookie != -1) {
                HeaderValues headerValues = headers.fiCurrent(fiCookie);

                int valueSize = headerValues.size() - 1; //null between the characters
                for (int i = 0; i < headerValues.size(); ++i) {
                    String val = headerValues.get(i);
                    valueSize += val.length();
                }
                int totalSize = 8 + headerValues.getHeaderName().length() + valueSize; // 8 == two ints for name and value sizes

                if (totalSize > inputBuffer.limit()) {
                    //todo: support large single headers
                    throw UndertowMessages.MESSAGES.headersTooLargeToFitInHeapBuffer();
                } else if (totalSize > inputBuffer.remaining()) {
                    allHeaderBuffers = doDeflate(inputBuffer, outputBuffer, currentPooled, allHeaderBuffers);
                    if(allHeaderBuffers != null) {
                        currentPooled = allHeaderBuffers[allHeaderBuffers.length - 1];
                    }
                    inputBuffer.clear();
                    outputBuffer.clear();
                }

                //TODO: for now it just fails if there are too many headers
                SpdyProtocolUtils.putInt(inputBuffer, headerValues.getHeaderName().length());
                for (int i = 0; i < headerValues.getHeaderName().length(); ++i) {
                    inputBuffer.put((byte) (Character.toLowerCase((char) headerValues.getHeaderName().byteAt(i))));
                }
                SpdyProtocolUtils.putInt(inputBuffer, valueSize);
                for (int i = 0; i < headerValues.size(); ++i) {
                    String val = headerValues.get(i);
                    for (int j = 0; j < val.length(); ++j) {
                        char c = val.charAt(j);
                        if(c != '\r' && c != '\n') {
                            inputBuffer.put((byte) c);
                        } else {
                            inputBuffer.put((byte)' ');
                        }
                    }
                    if (i != headerValues.size() - 1) {
                        inputBuffer.put((byte) 0);
                    }
                }
                fiCookie = headers.fiNext(fiCookie);
            }

            allHeaderBuffers = doDeflate(inputBuffer, outputBuffer, currentPooled, allHeaderBuffers);

            int totalLength;
            if (allHeaderBuffers != null) {
                totalLength = -8;
                for (PooledByteBuffer b : allHeaderBuffers) {
                    totalLength += b.getBuffer().position();
                }
            } else {
                totalLength = firstBuffer.position() - 8;
            }

            SpdyProtocolUtils.putInt(firstBuffer, ((isWritesShutdown() && !getBuffer().hasRemaining() ? SpdyChannel.FLAG_FIN : 0) << 24) | (unidirectional ? SpdyChannel.FLAG_UNIDIRECTIONAL : 0) << 24 | totalLength, 4);

        } finally {
            inPooled.close();
            outPooled.close();
        }
        return allHeaderBuffers;
    }


    protected abstract SendFrameHeader createFrameHeaderImpl();

    /**
     * This method should be called before sending. It will return the amount of
     * data that can be sent, taking into account the stream and connection flow
     * control windows, and the toSend parameter.
     * <p>
     * It will decrement the flow control windows by the amount that can be sent,
     * so this method should only be called as a frame is being queued.
     *
     * @return The number of bytes that can be sent
     */
    protected synchronized int grabFlowControlBytes(int toSend) {
        if(toSend == 0) {
            return 0;
        }
        int newWindowSize = this.getChannel().getInitialWindowSize();
        int settingsDelta = newWindowSize - this.initialWindowSize;
        //first adjust for any settings frame updates
        this.initialWindowSize = newWindowSize;
        this.flowControlWindow += settingsDelta;

        int min = Math.min(toSend, this.flowControlWindow);
        int actualBytes = this.getChannel().grabFlowControlBytes(min);
        this.flowControlWindow -= actualBytes;
        return actualBytes;
    }

    synchronized void updateFlowControlWindow(final int delta) throws IOException {
        boolean exhausted = flowControlWindow == 0;
        flowControlWindow += delta;
        if (exhausted) {
            getChannel().notifyFlowControlAllowed();
            if (isWriteResumed()) {
                resumeWritesInternal(true);
            }
        }
    }


    private PooledByteBuffer[] doDeflate(ByteBuffer inputBuffer, ByteBuffer outputBuffer, PooledByteBuffer currentPooled, PooledByteBuffer[] allHeaderBuffers) {
        Deflater deflater = getDeflater();
        deflater.setInput(inputBuffer.array(), inputBuffer.arrayOffset(), inputBuffer.position());

        int deflated;
        do {
            deflated = deflater.deflate(outputBuffer.array(), outputBuffer.arrayOffset(), outputBuffer.remaining(), Deflater.SYNC_FLUSH);
            if (deflated <= currentPooled.getBuffer().remaining()) {
                currentPooled.getBuffer().put(outputBuffer.array(), outputBuffer.arrayOffset(), deflated);
            } else {
                int pos = outputBuffer.arrayOffset();
                int remaining = deflated;
                ByteBuffer current = currentPooled.getBuffer();
                do {
                    int toPut = Math.min(current.remaining(), remaining);
                    current.put(outputBuffer.array(), pos, toPut);
                    pos += toPut;
                    remaining -= toPut;
                    if (remaining > 0) {
                        allHeaderBuffers = allocateAll(allHeaderBuffers, currentPooled);
                        currentPooled = allHeaderBuffers[allHeaderBuffers.length - 1];
                        current = currentPooled.getBuffer();
                    }
                } while (remaining > 0);
            }
        } while (!deflater.needsInput());
        return allHeaderBuffers;
    }

    protected abstract Deflater getDeflater();

    protected PooledByteBuffer[] allocateAll(PooledByteBuffer[] allHeaderBuffers, PooledByteBuffer currentBuffer) {
        PooledByteBuffer[] ret;
        if (allHeaderBuffers == null) {
            ret = new PooledByteBuffer[2];
            ret[0] = currentBuffer;
            ret[1] = getChannel().getBufferPool().allocate();
        } else {
            ret = new PooledByteBuffer[allHeaderBuffers.length + 1];
            System.arraycopy(allHeaderBuffers, 0, ret, 0, allHeaderBuffers.length);
            ret[ret.length - 1] = getChannel().getBufferPool().allocate();
        }
        return ret;
    }

    /**
     * Method that is invoked when the stream is reset.
     */
    void rstStream() {
        if(reset) {
            return;
        }
        reset = true;
        IoUtils.safeClose(this);
        getChannel().removeStreamSink(getStreamId());
    }
}
