package io.undertow.spdy;

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
abstract class SpdyHeaderBlockParser extends PushBackParser {

    private final SpdyChannel channel;

    private int numHeaders = -1;
    private int readHeaders = 0;
    private final HeaderMap headerMap = new HeaderMap();

    private final Inflater inflater;

    //state used for parsing headers
    private HttpString currentHeader;
    private ByteArrayOutputStream partialValue;
    private int remainingData;


    public SpdyHeaderBlockParser(Pool<ByteBuffer> bufferPool, SpdyChannel channel, int frameLength, Inflater inflater) {
        super(bufferPool, frameLength);
        this.channel = channel;
        this.inflater = inflater;
    }

    @Override
    protected void handleData(ByteBuffer resource) throws IOException {
        if (!handleBeforeHeader(resource)) {
            return;
        }
        Pooled<ByteBuffer> outPooled = channel.getHeapBufferPool().allocate();
        Pooled<ByteBuffer> inPooled = channel.getHeapBufferPool().allocate();
        try {
            ByteBuffer outputBuffer = outPooled.getResource();
            ByteBuffer inPooledResource = inPooled.getResource();
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
                    } else {
                        outputBuffer.position(outputBuffer.position() + copied);
                        handleDecompressedData(outputBuffer);
                    }
                }
            }
        } finally {
            inPooled.free();
            outPooled.free();
        }
    }

    protected abstract boolean handleBeforeHeader(ByteBuffer resource);


    private void handleDecompressedData(ByteBuffer data) throws IOException {
        data.flip();

        if (numHeaders == -1) {
            numHeaders = (data.get() & 0xFF) << 24;
            numHeaders += (data.get() & 0xFF) << 16;
            numHeaders += (data.get() & 0xFF) << 8;
            numHeaders += (data.get() & 0xFF);
        }
        while (readHeaders < numHeaders) {
            if (currentHeader == null && partialValue == null) {
                if (data.remaining() < 4) {
                    data.compact();
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
                    data.clear();
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
                    partialValue.write(data.array(), data.arrayOffset() + data.remaining(), data.remaining());
                    data.clear();
                    return;
                }
            }
            if (partialValue == null) {
                if (data.remaining() < 4) {
                    data.compact();
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
                            headerMap.add(currentHeader, new String(array, start, i - start - 1, "UTF-8"));
                            start = i + 1;
                        }
                    }
                    headerMap.add(currentHeader, new String(array, start, end - start, "UTF-8"));
                    currentHeader = null;
                    data.position(data.position() + valueLength);
                } else {
                    remainingData = valueLength - data.remaining();
                    int start = data.arrayOffset() + data.position();
                    int end = start + valueLength;
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
                    data.clear();
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
                    partialValue = new ByteArrayOutputStream();
                    partialValue.write(data.array(), data.arrayOffset() + data.remaining(), data.remaining());
                    data.clear();
                    return;
                }
            }
            this.readHeaders++;
        }
        data.compact();
    }

    HeaderMap getHeaderMap() {
        return headerMap;
    }
}
