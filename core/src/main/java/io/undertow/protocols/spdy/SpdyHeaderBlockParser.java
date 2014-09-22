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

import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import org.xnio.Pool;
import org.xnio.Pooled;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Parser for SPDY compressed header blocks
 *
 * @author Stuart Douglas
 */
abstract class SpdyHeaderBlockParser extends SpdyPushBackParser {

    private final SpdyChannel channel;

    private int numHeaders = -1;
    private int readHeaders = 0;
    private final HeaderMap headerMap = new HeaderMap();

    private final Inflater inflater;

    //state used for parsing headers
    private HttpString currentHeader;
    private ByteArrayOutputStream partialValue;
    private int remainingData;
    private boolean beforeHeadersHandled = false;
    private byte[] dataOverflow;


    public SpdyHeaderBlockParser(Pool<ByteBuffer> bufferPool, SpdyChannel channel, int frameLength, Inflater inflater) {
        super(frameLength);
        this.channel = channel;
        this.inflater = inflater;
    }

    @Override
    protected void handleData(ByteBuffer resource) throws IOException {
        if(!beforeHeadersHandled) {
            if (!handleBeforeHeader(resource)) {
                return;
            }
        }
        beforeHeadersHandled = true;
        Pooled<ByteBuffer> outPooled = channel.getHeapBufferPool().allocate();
        Pooled<ByteBuffer> inPooled = channel.getHeapBufferPool().allocate();

        boolean extraOutput = false;
        try {
            ByteBuffer outputBuffer = outPooled.getResource();
            ByteBuffer inPooledResource = inPooled.getResource();
            if(dataOverflow != null) {
                outputBuffer.put(dataOverflow);
                dataOverflow = null;
                extraOutput = true;
            }
            byte[] inputBuffer = inPooledResource.array();
            while (resource.hasRemaining()) {
                int rem = resource.remaining();
                if (rem > inputBuffer.length) {
                    resource.get(inputBuffer, inPooledResource.arrayOffset(), inPooledResource.limit());
                } else {
                    resource.get(inputBuffer, inPooledResource.arrayOffset(), resource.remaining());
                }
                int inputLength = Math.min(rem, inPooledResource.limit());
                inflater.setInput(inputBuffer, inPooledResource.arrayOffset(), inputLength);
                while (!inflater.needsInput()) {
                    int copied = 0;
                    try {
                        copied = inflater.inflate(outputBuffer.array(), outputBuffer.arrayOffset() + outputBuffer.position(), outputBuffer.remaining());
                    } catch (DataFormatException e) {
                        throw new StreamErrorException(StreamErrorException.PROTOCOL_ERROR);
                    }
                    if (copied == 0 && inflater.needsDictionary()) {
                        inflater.setDictionary(SpdyProtocolUtils.SPDY_DICT);
                    } else if(copied > 0) {
                        outputBuffer.position(outputBuffer.position() + copied);
                        handleDecompressedData(outputBuffer);
                        if(outputBuffer.hasRemaining()) {
                            outputBuffer.compact();
                            extraOutput = true;
                        } else {
                            extraOutput = false;
                            outputBuffer.clear();
                        }
                    }
                }
            }
        } finally {
            if(extraOutput) {
                outPooled.getResource().flip();
                dataOverflow = new byte[outPooled.getResource().remaining()];
                outPooled.getResource().get(dataOverflow);
            }
            inPooled.free();
            outPooled.free();
        }
    }

    protected abstract boolean handleBeforeHeader(ByteBuffer resource);


    private void handleDecompressedData(ByteBuffer data) throws IOException {
        data.flip();

        if (numHeaders == -1) {

            if(data.remaining() < 4) {
                return;
            }
            numHeaders = (data.get() & 0xFF) << 24;
            numHeaders += (data.get() & 0xFF) << 16;
            numHeaders += (data.get() & 0xFF) << 8;
            numHeaders += (data.get() & 0xFF);
        }
        while (readHeaders < numHeaders) {
            if (currentHeader == null && partialValue == null) {
                if (data.remaining() < 4) {
                    return;
                }
                int nameLength = (data.get() & 0xFF) << 24;
                nameLength += (data.get() & 0xFF) << 16;
                nameLength += (data.get() & 0xFF) << 8;
                nameLength += (data.get() & 0xFF);
                if (nameLength == 0) {
                    throw new StreamErrorException(StreamErrorException.PROTOCOL_ERROR);
                }

                if (data.remaining() >= nameLength) {
                    currentHeader = new HttpString(data.array(), data.arrayOffset() + data.position(), nameLength);
                    data.position(data.position() + nameLength);
                } else {
                    remainingData = nameLength - data.remaining();
                    partialValue = new ByteArrayOutputStream();
                    partialValue.write(data.array(), data.arrayOffset() + data.position(), data.remaining());
                    data.position(data.limit());
                    return;
                }
            } else if (currentHeader == null && partialValue != null) {
                if (data.remaining() >= remainingData) {
                    partialValue.write(data.array(), data.arrayOffset() + data.position(), remainingData);
                    currentHeader = new HttpString(partialValue.toByteArray());
                    data.position(data.position() + remainingData);
                    this.remainingData = -1;
                    this.partialValue = null;
                } else {
                    remainingData = remainingData - data.remaining();
                    partialValue.write(data.array(), data.arrayOffset() + data.position(), data.remaining());
                    data.position(data.limit());
                    return;
                }
            }
            if (partialValue == null) {
                if (data.remaining() < 4) {
                    return;
                }
                int valueLength = (data.get() & 0xFF) << 24;
                valueLength += (data.get() & 0xFF) << 16;
                valueLength += (data.get() & 0xFF) << 8;
                valueLength += (data.get() & 0xFF);
                //headers can have multiple values, separated by a single null character

                if (data.remaining() >= valueLength) {
                    int start = data.arrayOffset() + data.position();
                    int end = start + valueLength;
                    byte[] array = data.array();
                    for (int i = start; i < end; ++i) {
                        if (array[i] == 0) {
                            headerMap.add(currentHeader, new String(array, start, i - start, "UTF-8"));
                            start = i + 1;
                        }
                    }
                    headerMap.add(currentHeader, new String(array, start, end - start, "UTF-8"));
                    currentHeader = null;
                    data.position(data.position() + valueLength);
                } else {
                    remainingData = valueLength - data.remaining();
                    int start = data.arrayOffset() + data.position();
                    int end = start + data.remaining();
                    byte[] array = data.array();
                    for (int i = start; i < end; ++i) {
                        if (array[i] == 0) {
                            String headerValue = new String(array, start, i - start - 1, "UTF-8");
                            headerMap.add(currentHeader, headerValue);
                            start = i + 1;
                        }
                    }
                    partialValue = new ByteArrayOutputStream();
                    partialValue.write(array, start, end - start);
                    data.position(data.limit());
                    return;
                }
            } else {
                if (data.remaining() >= remainingData) {
                    partialValue.write(data.array(), data.arrayOffset() + data.position(), remainingData);
                    byte[] completeData = partialValue.toByteArray();
                    int start = 0;
                    int end = completeData.length;
                    for (int i = start; i < end; ++i) {
                        if (completeData[i] == 0) {
                            headerMap.add(currentHeader, new String(completeData, start, i - start - 1, "UTF-8"));
                            start = i + 1;
                        }
                    }
                    headerMap.add(currentHeader, new String(completeData, start, end - start, "UTF-8"));
                    data.position(data.position() + remainingData);
                    currentHeader = null;
                    this.remainingData = -1;
                    this.partialValue = null;
                } else {
                    remainingData = remainingData - data.remaining();
                    partialValue.write(data.array(), data.arrayOffset() + data.position(), data.remaining());
                    data.position(data.limit());
                    return;
                }
            }
            this.readHeaders++;
        }
    }

    HeaderMap getHeaderMap() {
        return headerMap;
    }
}
