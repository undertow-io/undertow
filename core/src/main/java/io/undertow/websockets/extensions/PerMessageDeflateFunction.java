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

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import io.undertow.websockets.core.StreamSinkFrameChannel;
import io.undertow.websockets.core.StreamSourceFrameChannel;
import io.undertow.websockets.core.WebSocketLogger;
import io.undertow.websockets.core.WebSocketMessages;

/**
 * Implementation of {@code permessage-deflate} WebSocket Extension.
 * <p>
 * This implementation supports parameters: {@code server_no_context_takeover, client_no_context_takeover} .
 * <p>
 * This implementation does not support parameters: {@code server_max_window_bits, client_max_window_bits} .
 * <p>
 * It uses the DEFLATE implementation algorithm packaged on {@link java.util.zip.Deflater} and {@link java.util.zip.Inflater} classes.
 *
 * @see <a href="http://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-18">Compression Extensions for WebSocket</a>
 *
 * @author Lucas Ponce
 */
public class PerMessageDeflateFunction implements ExtensionFunction {

    private boolean compressContextTakeover;
    private boolean decompressContextTakeover;

    private final boolean client;
    private final int deflaterLevel;

    private Inflater decompress;
    private Deflater compress;

    /**
     * Pool for aux buffers used in compression/decompression tasks.
     */
    private static final ThreadLocal<byte[][]> pool = new ThreadLocal<byte[][]>() {
        protected byte[][] initialValue() {
            return new byte[2][];
        }
    };

    private byte[] input;
    private byte[] output;

    private static final int OFFSET = 64;

    public static final byte[] TAIL = new byte[]{0x00, 0x00, (byte)0xFF, (byte)0xFF};


    /**
     * Create a new {@code PerMessageDeflateExtension} instance.
     *
     * @param client                    flag for client ({@code true }) context or server ({@code false }) context
     * @param deflaterLevel             the level of configuration of DEFLATE algorithm implementation
     * @param compressContextTakeover   flag for compressor context takeover or without compressor context
     * @param decompressContextTakeover flag for decompressor context takeover or without decompressor context
     */
    public PerMessageDeflateFunction(final boolean client, final int deflaterLevel, boolean compressContextTakeover, boolean decompressContextTakeover) {
        this.client = client;
        this.deflaterLevel = deflaterLevel;
        decompress = new Inflater(true);
        compress = new Deflater(this.deflaterLevel, true);
        this.compressContextTakeover = compressContextTakeover;
        this.decompressContextTakeover = decompressContextTakeover;
        input = null;
        output = null;
    }

    @Override
    public boolean isClient() {
        return client;
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
    public void beforeWrite(StreamSinkFrameChannel channel, ExtensionByteBuffer extBuf, int position, int length)  throws IOException {
        if (extBuf == null || length == 0) return;

        initBuffers(Math.max(extBuf.getInput().capacity(), length));

        for (int i = 0; i < length; i++) {
            input[i] = extBuf.get(position + i);
        }
        compress.setInput(input, 0, length);

        int n;
        while (!compress.needsInput() && !compress.finished()) {
            n = compress.deflate(output, 0, output.length, Deflater.SYNC_FLUSH);
            if (n != 0) {
                for (int i = 0; i < n; i++) {
                    extBuf.put(output[i]);
                }
            }
        }
    }

    @Override
    public void beforeFlush(StreamSinkFrameChannel channel, ExtensionByteBuffer extBuf, int position, int length) throws IOException {
        /*
            Add a padding DEFLATE block without TAIL at the end of the message.

            From: http://tools.ietf.org/html/draft-ietf-hybi-permessage-compression-18#page-21

           3.  Remove 4 octets (that are 0x00 0x00 0xff 0xff) from the tail end.
               After this step, the last octet of the compressed data contains
               (possibly part of) the DEFLATE header bits with the "BTYPE" bits
               set to 00.

            Padding DEFLATE block:                  (byte)0, (byte)0, (byte)0, (byte)0xFF, (byte)0xFF
            Padding DEFLATE block witout TAIL:      (byte)0
         */
        extBuf.put((byte) 0);

        if (!compressContextTakeover) {
            compress.reset();
        }
    }

    @Override
    public void afterRead(final StreamSourceFrameChannel channel, final ExtensionByteBuffer extBuf, final int position, final int length) throws IOException {
        if (extBuf == null) return;

        initBuffers(Math.max(extBuf.getInput().capacity(), length));

        if (length > 0) {
            for (int i = 0; i < length; i++) {
                input[i] = extBuf.get(position + i);
            }
            decompress.setInput(input, 0, length);

            int n;
            while (!decompress.needsInput() && !decompress.finished()) {
                try {
                    n = decompress.inflate(output, 0, output.length);
                    if (n > 0) {
                        for (int i = 0; i < n; i++) {
                            extBuf.put(output[i]);
                        }
                    }
                } catch (DataFormatException e) {
                    WebSocketLogger.EXTENSION_LOGGER.debug(e.getMessage(), e);
                    throw WebSocketMessages.MESSAGES.badCompressedPayload();
                }
            }
        }

        if (length == -1) {
            /*
                Process TAIL bytes at the end of the message.
             */
            int n;

            decompress.setInput(TAIL);
            while (!decompress.needsInput() && !decompress.finished()) {
                try {
                    n = decompress.inflate(output, 0, output.length);
                    if (n > 0) {
                        for (int i = 0; i < n; i++) {
                            extBuf.put(output[i]);
                        }
                    }
                } catch (DataFormatException e) {
                    WebSocketLogger.EXTENSION_LOGGER.debug(e.getMessage(), e);
                    throw WebSocketMessages.MESSAGES.badCompressedPayload();
                }

            }
        }

        if (length == -1 && !decompressContextTakeover) {
            decompress.reset();
        }
    }

    /**
     * Initialize input/output buffers used for compression/decompression tasks.
     *
     * @param length max capacity of internal buffers
     */
    private void initBuffers(int length) {
        if (input == null || output == null || input.length < length || output.length < (length + OFFSET)) {
            if (pool.get()[0] == null || pool.get()[0].length < length) {
                pool.get()[0] = new byte[length];
            }
            if (pool.get()[1] == null || pool.get()[1].length < (length + OFFSET)) {
                pool.get()[1] = new byte[length + OFFSET];
            }
            input = pool.get()[0];
            output = pool.get()[1];
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PerMessageDeflateFunction)) return false;

        PerMessageDeflateFunction that = (PerMessageDeflateFunction) o;

        if (client != that.client) return false;
        if (compressContextTakeover != that.compressContextTakeover) return false;
        if (decompressContextTakeover != that.decompressContextTakeover) return false;
        if (deflaterLevel != that.deflaterLevel) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (compressContextTakeover ? 1 : 0);
        result = 31 * result + (decompressContextTakeover ? 1 : 0);
        result = 31 * result + (client ? 1 : 0);
        result = 31 * result + deflaterLevel;
        return result;
    }
}
