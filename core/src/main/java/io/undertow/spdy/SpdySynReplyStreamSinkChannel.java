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

package io.undertow.spdy;

import io.undertow.server.protocol.framed.SendFrameHeader;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.xnio.Pooled;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;

/**
 * @author Stuart Douglas
 */
public class SpdySynReplyStreamSinkChannel extends SpdyStreamStreamSinkChannel {

    private final HeaderMap headers = new HeaderMap();

    private boolean first = true;
    private final Deflater deflater;


    SpdySynReplyStreamSinkChannel(SpdyChannel channel, int streamId, Deflater deflater) {
        super(channel, streamId);
        this.deflater = deflater;
    }

    @Override
    protected SendFrameHeader createFrameHeaderImpl() {
        Pooled<ByteBuffer> header = getChannel().getHeapBufferPool().allocate();
        ByteBuffer buffer = header.getResource();
        if (first) {
            Pooled<ByteBuffer> outPooled = getChannel().getHeapBufferPool().allocate();
            Pooled<ByteBuffer> inPooled = getChannel().getHeapBufferPool().allocate();
            try {
                ByteBuffer inputBuffer = inPooled.getResource();
                ByteBuffer outputBuffer = outPooled.getResource();


                first = false;
                int firstInt = SpdyChannel.CONTROL_FRAME | (getChannel().getSpdyVersion() << 16) | 2;
                SpdyProtocolUtils.putInt(buffer, firstInt);
                SpdyProtocolUtils.putInt(buffer, 0); //we back fill the length
                HeaderMap headers = this.headers;

                SpdyProtocolUtils.putInt(buffer, getStreamId());


                headers.remove(Headers.CONNECTION); //todo: should this be here?
                headers.remove(Headers.KEEP_ALIVE);
                headers.remove(Headers.TRANSFER_ENCODING);

                SpdyProtocolUtils.putInt(inputBuffer, headers.size());

                long fiCookie = headers.fastIterateNonEmpty();
                while (fiCookie != -1) {
                    HeaderValues headerValues = headers.fiCurrent(fiCookie);
                    //TODO: for now it just fails if there are too many headers
                    SpdyProtocolUtils.putInt(inputBuffer, headerValues.getHeaderName().length());
                    for (int i = 0; i < headerValues.getHeaderName().length(); ++i) {
                        inputBuffer.put((byte) (Character.toLowerCase((char) headerValues.getHeaderName().byteAt(i))));
                    }
                    int pos = inputBuffer.position();
                    SpdyProtocolUtils.putInt(inputBuffer, 0); //size, back fill

                    int size = headerValues.size() - 1; //null between the characters

                    for (int i = 0; i < headerValues.size(); ++i) {
                        String val = headerValues.get(i);
                        size += val.length();
                        for (int j = 0; j < val.length(); ++j) {
                            inputBuffer.put((byte) val.charAt(j));
                        }
                        if (i != headerValues.size() - 1) {
                            inputBuffer.put((byte) 0);
                        }
                    }
                    SpdyProtocolUtils.putInt(inputBuffer, size, pos);
                    fiCookie = headers.fiNext(fiCookie);
                }

                deflater.setInput(inputBuffer.array(), inputBuffer.arrayOffset(), inputBuffer.position());

                int deflated;
                do {
                    deflated = deflater.deflate(outputBuffer.array(), outputBuffer.arrayOffset(), outputBuffer.remaining(), Deflater.SYNC_FLUSH);
                    buffer.put(outputBuffer.array(), outputBuffer.arrayOffset(), deflated);
                } while (deflated == outputBuffer.remaining());
                SpdyProtocolUtils.putInt(buffer, ((isWritesShutdown() && !getBuffer().hasRemaining() ? SpdyChannel.FLAG_FIN : 0) << 24) | (buffer.position() - 8), 4);

            } finally {
                inPooled.free();
                outPooled.free();
            }
        }
        int remainingInBuffer = 0;
        if (getBuffer().remaining() > 0) {
            int fcWindow = grabFlowControlBytes(getBuffer().remaining());
            if (fcWindow > 0) {
                remainingInBuffer = getBuffer().remaining() - fcWindow;
                getBuffer().limit(buffer.position() + fcWindow);
                SpdyProtocolUtils.putInt(buffer, getStreamId());
                SpdyProtocolUtils.putInt(buffer, ((isWritesShutdown() ? SpdyChannel.FLAG_FIN : 0) << 24) + fcWindow);
            } else {
                remainingInBuffer = getBuffer().remaining();
            }
        }
        header.getResource().flip();
        if (!header.getResource().hasRemaining()) {
            return new SendFrameHeader(remainingInBuffer, null);
        }
        return new SendFrameHeader(remainingInBuffer, header);
    }

    public HeaderMap getHeaders() {
        return headers;
    }
}
