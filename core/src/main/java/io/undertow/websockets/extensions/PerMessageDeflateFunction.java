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

package io.undertow.websockets.extensions;

import io.undertow.util.ImmediatePooled;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.core.WebSocketMessages;
import org.xnio.Buffers;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Implementation of {@code permessage-deflate} WebSocket Extension.
 * <p/>
 * This implementation supports parameters: {@code server_no_context_takeover, client_no_context_takeover} .
 * <p/>
 * This implementation does not support parameters: {@code server_max_window_bits, client_max_window_bits} .
 * <p/>
 * It uses the DEFLATE implementation algorithm packaged on {@link Deflater} and {@link Inflater} classes.
 *
 * @author Lucas Ponce
 * @see <a href="http://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-18">Compression Extensions for WebSocket</a>
 */
public class PerMessageDeflateFunction implements ExtensionFunction {

    public static final byte[] TAIL = new byte[]{0x00, 0x00, (byte) 0xFF, (byte) 0xFF};

    private final int deflaterLevel;
    private final boolean compressContextTakeover;
    private final boolean decompressContextTakeover;
    private final Inflater decompress;
    private final Deflater compress;

    /**
     * Create a new {@code PerMessageDeflateExtension} instance.
     *
     * @param deflaterLevel             the level of configuration of DEFLATE algorithm implementation
     * @param compressContextTakeover   flag for compressor context takeover or without compressor context
     * @param decompressContextTakeover flag for decompressor context takeover or without decompressor context
     */
    public PerMessageDeflateFunction(final int deflaterLevel, boolean compressContextTakeover, boolean decompressContextTakeover) {
        this.deflaterLevel = deflaterLevel;
        this.decompress = new Inflater(true);
        this.compress = new Deflater(this.deflaterLevel, true);
        this.compressContextTakeover = compressContextTakeover;
        this.decompressContextTakeover = decompressContextTakeover;
    }

    @Override
    public int writeRsv(int rsv) {
        return rsv | RSV1;
    }

    @Override
    public boolean hasExtensionOpCode() {
        return false;
    }

    @Override
    public synchronized Pooled<ByteBuffer> transformForWrite(Pooled<ByteBuffer> pooledBuffer, WebSocketChannel channel) throws IOException {
        ByteBuffer buffer = pooledBuffer.getResource();
        if (buffer.hasArray()) {
            compress.setInput(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        } else {
            compress.setInput(Buffers.take(buffer));
        }

        Pooled<ByteBuffer> output = allocateBufferWithArray(channel, 0); // first pass
        ByteBuffer outputBuffer = output.getResource();

        try {
            while (!compress.needsInput() && !compress.finished()) {
                if (!outputBuffer.hasRemaining()) {
                    output = largerBuffer(output, channel, outputBuffer.capacity() * 2);
                    outputBuffer = output.getResource();
                }

                int n = compress.deflate(
                        outputBuffer.array(),
                        outputBuffer.arrayOffset() + outputBuffer.position(),
                        outputBuffer.remaining(),
                        Deflater.SYNC_FLUSH);
                outputBuffer.position(outputBuffer.position() + n);
            }
        } finally {
            // Free the buffer AFTER compression so it doesn't get re-used out from under us
            pooledBuffer.free();
        }

        if (!outputBuffer.hasRemaining()) {
            output = largerBuffer(output, channel, outputBuffer.capacity() + 1);
            outputBuffer = output.getResource();
        }

        outputBuffer.put((byte) 0);
        outputBuffer.flip();

        if (!compressContextTakeover) {
            compress.reset();
        }

        return output;
    }

    private Pooled<ByteBuffer> largerBuffer(Pooled<ByteBuffer> smaller, WebSocketChannel channel, int newSize) {
        ByteBuffer smallerBuffer = smaller.getResource();

        smallerBuffer.flip();

        Pooled<ByteBuffer> larger = allocateBufferWithArray(channel, newSize);
        larger.getResource().put(smallerBuffer);

        smaller.free();
        return larger;
    }

    private Pooled<ByteBuffer> allocateBufferWithArray(WebSocketChannel channel, int size) {
        if (size > 0) {
            // TODO use newer XNIO sized pool thingies smartly
            return new ImmediatePooled<>(ByteBuffer.allocate(size));
        }

        Pooled<ByteBuffer> pooled = channel.getBufferPool().allocate();
        if (pooled.getResource().hasArray()) {
            return pooled;
        } else {
            int pooledSize = pooled.getResource().remaining();
            pooled.free();
            return allocateBufferWithArray(channel, pooledSize);
        }
    }

    @Override
    public synchronized Pooled<ByteBuffer> transformForRead(Pooled<ByteBuffer> pooledBuffer, WebSocketChannel channel, boolean lastFragmentOfFrame) throws IOException {
        ByteBuffer buffer = pooledBuffer.getResource();

        if (buffer.hasArray()) {
            decompress.setInput(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        } else {
            decompress.setInput(Buffers.take(buffer));
        }

        Pooled<ByteBuffer> output = allocateBufferWithArray(channel, 0); // first pass

        try {
            output = decompress(channel, output);
        } finally {
            // Free the buffer AFTER decompression so it doesn't get re-used out from under us
            pooledBuffer.free();
        }

        if (lastFragmentOfFrame) {
            decompress.setInput(TAIL);
            output = decompress(channel, output);
        }

        output.getResource().flip();

        if (lastFragmentOfFrame && !decompressContextTakeover) {
            decompress.reset();
        }

        return output;
    }

    private Pooled<ByteBuffer> decompress(WebSocketChannel channel, Pooled<ByteBuffer> pooled) throws IOException {
        ByteBuffer buffer = pooled.getResource();

        while (!decompress.needsInput() && !decompress.finished()) {
            if (!buffer.hasRemaining()) {
                pooled = largerBuffer(pooled, channel, buffer.capacity() * 2);
                buffer = pooled.getResource();
            }

            int n;

            try {
                n = decompress.inflate(buffer.array(),
                        buffer.arrayOffset() + buffer.position(),
                        buffer.remaining());
            } catch (DataFormatException e) {
                WebSocketLogger.EXTENSION_LOGGER.debug(e.getMessage(), e);
                throw WebSocketMessages.MESSAGES.badCompressedPayload(e);
            }
            buffer.position(buffer.position() + n);
        }

        return pooled;
    }

    @Override
    public void dispose() {
        // Call end so that native zlib resources can be immediately released rather than relying on finalizer
        compress.end();
        decompress.end();
    }
}
