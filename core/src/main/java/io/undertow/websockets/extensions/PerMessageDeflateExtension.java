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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import io.undertow.websockets.WebSocketExtension;
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
public class PerMessageDeflateExtension implements ExtensionHandshake, ExtensionFunction {

    private static final String PERMESSAGE_DEFLATE = "permessage-deflate";
    private static final String SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover";
    private static final String CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover";
    private static final String SERVER_MAX_WINDOW_BITS = "server_max_window_bits";
    private static final String CLIENT_MAX_WINDOW_BITS = "client_max_window_bits";

    private final Set<String> incompatibleExtensions = new HashSet<>();

    private volatile boolean compressContextTakeover;
    private volatile boolean decompressContextTakeover;

    private final boolean client;
    private final int deflaterLevel;

    private Inflater decompress;
    private Deflater compress;

    /**
     * Default configuration for DEFLATE algorithm implementation
     */
    public static final int DEFAULT_DEFLATER = Deflater.BEST_SPEED;

    public static final byte[] TAIL = new byte[]{0x00, 0x00, (byte)0xFF, (byte)0xFF};

    public PerMessageDeflateExtension() {
        this(false);
    }

    /**
     * Create a new {@code PerMessageDeflateExtension} instance.
     *
     * @param client indicate if extension is configured in client ({@code true }) context or server ({@code false }) context.
     */
    public PerMessageDeflateExtension(final boolean client) {
        this(client, DEFAULT_DEFLATER);
    }

    /**
     * Create a new {@code PerMessageDeflateExtension} instance.
     *
     * @param client        indicate if extension is configured in client ({@code true }) context or server ({@code false }) context
     * @param deflaterLevel the level of configuration of DEFLATE algorithm implementation
     */
    public PerMessageDeflateExtension(final boolean client, final int deflaterLevel) {
        this(client, deflaterLevel, true, true);
    }

    /**
     * Create a new {@code PerMessageDeflateExtension} instance.
     *
     * @param client                    flag for client ({@code true }) context or server ({@code false }) context
     * @param compressContextTakeover   flag for compressor context takeover or without compressor context
     * @param decompressContextTakeover flag for decompressor context takeover or without decompressor context
     */
    public PerMessageDeflateExtension(final boolean client, boolean compressContextTakeover, boolean decompressContextTakeover) {
        this(client, DEFAULT_DEFLATER, compressContextTakeover, decompressContextTakeover);
    }

    /**
     * Create a new {@code PerMessageDeflateExtension} instance.
     *
     * @param client                    flag for client ({@code true }) context or server ({@code false }) context
     * @param deflaterLevel             the level of configuration of DEFLATE algorithm implementation
     * @param compressContextTakeover   flag for compressor context takeover or without compressor context
     * @param decompressContextTakeover flag for decompressor context takeover or without decompressor context
     */
    public PerMessageDeflateExtension(final boolean client, final int deflaterLevel, boolean compressContextTakeover, boolean decompressContextTakeover) {
        this.client = client;
        this.deflaterLevel = deflaterLevel;
        /*
            This extension is incompatible with multiple instances of same extension in the same Endpoint.
         */
        incompatibleExtensions.add(PERMESSAGE_DEFLATE);
        decompress = new Inflater(true);
        compress = new Deflater(this.deflaterLevel, true);
        this.compressContextTakeover = compressContextTakeover;
        this.decompressContextTakeover = decompressContextTakeover;
    }

    @Override
    public ExtensionFunction create() {
        return new PerMessageDeflateExtension(client, deflaterLevel, compressContextTakeover, decompressContextTakeover);
    }

    @Override
    public boolean isClient() {
        return client;
    }

    @Override
    public String getName() {
        return PERMESSAGE_DEFLATE;
    }

    @Override
    public Set<String> getIncompatibleExtensions() {
        return incompatibleExtensions;
    }

    @Override
    public WebSocketExtension accept(final WebSocketExtension extension) {
        if (extension == null || !extension.getName().equals(getName())) return null;

        WebSocketExtension negotiated = new WebSocketExtension(extension.getName());

        if (extension.getParameters() == null || extension.getParameters().size() == 0) return negotiated;
        for (WebSocketExtension.Parameter parameter : extension.getParameters()) {
            if (parameter.getName().equals(SERVER_MAX_WINDOW_BITS)) {
                /*
                    Not supported
                 */
            } else if (parameter.getName().equals(CLIENT_MAX_WINDOW_BITS)) {
                /*
                    Not supported
                 */
            } else if (parameter.getName().equals(SERVER_NO_CONTEXT_TAKEOVER)) {
                negotiated.getParameters().add(parameter);
                if (client) {
                    decompressContextTakeover = false;
                } else {
                    compressContextTakeover = false;
                }
            } else if (parameter.getName().equals(CLIENT_NO_CONTEXT_TAKEOVER)) {
                negotiated.getParameters().add(parameter);
                if (client) {
                    compressContextTakeover = false;
                } else {
                    decompressContextTakeover = false;
                }
            } else {
                WebSocketLogger.EXTENSION_LOGGER.incorrectExtensionParameter(parameter);
                return null;
            }
        }
        return negotiated;
    }

    @Override
    public boolean isIncompatible(List<ExtensionHandshake> extensions) {
        for (ExtensionHandshake extension : extensions) {
            if (extension.getIncompatibleExtensions().contains(getName())) {
                return true;
            }
        }
        return false;
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

        byte[] input = new byte[length];
        for (int i = 0; i < length; i++) {
            input[i] = extBuf.get(position + i);
        }
        compress.setInput(input);

        byte[] output = new byte[length + 64];

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

        if (length > 0) {

            byte[] input;
            int inputLength = length;
            input = new byte[inputLength];

            for (int i = 0; i < length; i++) {
                input[i] = extBuf.get(position + i);
            }
            decompress.setInput(input);

            byte[] output = new byte[length + 64];

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
            byte[] outputTail = new byte[TAIL.length + 64];
            int n;

            decompress.setInput(TAIL);
            while (!decompress.needsInput() && !decompress.finished()) {
                try {
                    n = decompress.inflate(outputTail, 0, outputTail.length);
                    if (n > 0) {
                        for (int i = 0; i < n; i++) {
                            extBuf.put(outputTail[i]);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PerMessageDeflateExtension)) return false;

        PerMessageDeflateExtension that = (PerMessageDeflateExtension) o;

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