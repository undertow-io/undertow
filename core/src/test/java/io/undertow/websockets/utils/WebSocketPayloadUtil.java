/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
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
package io.undertow.websockets.utils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;
import java.util.zip.Deflater;

/**
 * @author Avishek Sarkar
 *
 * Generates raw WebSocket payloads for invariant testing.
 *
 * These payloads are crafted at the byte level — not via a WebSocket
 * client library — to test protocol-layer behavior before application
 * logic runs.
 *
 * Frame format (RFC 6455 Section 5.2):
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-------+-+-------------+-------------------------------+
 *  |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
 *  |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
 *  |N|V|V|V|       |S|             |   (if payload len==126/127)   |
 *  | |1|2|3|       |K|             |                               |
 *  +-+-+-+-+-------+-+-------------+-------------------------------+
 */
public final class WebSocketPayloadUtil {

    // Opcodes
    public static final int OP_CONTINUATION = 0x00;
    public static final int OP_TEXT         = 0x01;
    public static final int OP_BINARY       = 0x02;
    public static final int OP_CLOSE        = 0x08;
    public static final int OP_PING         = 0x09;
    public static final int OP_PONG         = 0x0A;

    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-5AB5AC82B665";

    private WebSocketPayloadUtil() {}

    /**
     * Build a valid HTTP Upgrade request with the given Origin.
     */
    public static byte[] buildUpgradeRequest(String host, int port, String path,
                                              String origin, String wsKey) {
        String request = "GET " + path + " HTTP/1.1\r\n" +
                "Host: " + host + ":" + port + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + wsKey + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "Origin: " + origin + "\r\n" +
                "\r\n";
        return request.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Generate a random Sec-WebSocket-Key (16 bytes, Base64 encoded).
     */
    public static String generateWebSocketKey() {
        byte[] key = new byte[16];
        new Random().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * Compute the expected Sec-WebSocket-Accept value for handshake validation.
     */
    public static String computeAcceptKey(String wsKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update((wsKey + WS_MAGIC).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Build a single masked WebSocket frame.
     * Client-to-server frames MUST be masked (RFC 6455 Section 5.1).
     */
    public static byte[] buildFrame(int opcode, boolean fin, byte[] payload) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // Byte 0: FIN + opcode
        int b0 = (fin ? 0x80 : 0x00) | (opcode & 0x0F);
        buf.write(b0);

        // Byte 1: MASK=1 + payload length
        int len = payload.length;
        if (len <= 125) {
            buf.write(0x80 | len); // MASK bit set
        } else if (len <= 65535) {
            buf.write(0x80 | 126);
            buf.write((len >> 8) & 0xFF);
            buf.write(len & 0xFF);
        } else {
            buf.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) {
                buf.write((int) ((len >> (8 * i)) & 0xFF));
            }
        }

        // Masking key (4 bytes)
        byte[] mask = new byte[4];
        new Random().nextBytes(mask);
        buf.write(mask, 0, 4);

        // Masked payload
        for (int i = 0; i < payload.length; i++) {
            buf.write(payload[i] ^ mask[i % 4]);
        }

        return buf.toByteArray();
    }

    /**
     * Build N Ping frames — for control frame flood testing.
     * Each Ping has a small payload to force Pong response.
     */
    public static byte[] buildPingFlood(int count) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] pingPayload = "p".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < count; i++) {
            byte[] frame = buildFrame(OP_PING, true, pingPayload);
            buf.write(frame, 0, frame.length);
        }
        return buf.toByteArray();
    }

    /**
     * Build a fragmentation bomb — millions of tiny continuation frames.
     * NO FIN=1 frame is ever sent. The server buffers all fragments
     * indefinitely, consuming memory while waiting for completion.
     * The finding is: how much memory is consumed before the server
     * enforces a per-fragment or total-buffer limit?
     */
    public static byte[] buildFragmentationBomb(int fragmentCount) {
        return buildFragmentationBomb(fragmentCount, 1024);
    }

    /**
     * Build a fragmentation bomb with configurable payload per fragment.
     * NO FIN=1 frame is ever sent. The server buffers all fragments
     * indefinitely, consuming memory while waiting for completion.
     */
    public static byte[] buildFragmentationBomb(int fragmentCount, int payloadPerFragment) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] payload = new byte[payloadPerFragment];
        java.util.Arrays.fill(payload, (byte) 'X');

        // First frame: text, FIN=0 (start of fragments)
        byte[] first = buildFrame(OP_TEXT, false, payload);
        buf.write(first, 0, first.length);

        // All subsequent frames: continuation, FIN=0 — never complete
        for (int i = 0; i < fragmentCount - 1; i++) {
            byte[] mid = buildFrame(OP_CONTINUATION, false, payload);
            buf.write(mid, 0, mid.length);
        }

        // NO final FIN=1 frame — server must buffer indefinitely
        return buf.toByteArray();
    }

    /**
     * Build a completed fragmented message (with FIN=1 at end).
     * Used for baselines to prove fragmentation reassembly works.
     */
    public static byte[] buildCompletedFragmentedMessage(int fragmentCount) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] oneBytePayload = "x".getBytes(StandardCharsets.UTF_8);

        byte[] first = buildFrame(OP_TEXT, false, oneBytePayload);
        buf.write(first, 0, first.length);

        for (int i = 0; i < fragmentCount - 2; i++) {
            byte[] mid = buildFrame(OP_CONTINUATION, false, oneBytePayload);
            buf.write(mid, 0, mid.length);
        }

        byte[] last = buildFrame(OP_CONTINUATION, true, oneBytePayload);
        buf.write(last, 0, last.length);

        return buf.toByteArray();
    }

    /**
     * Build an HTTP Upgrade request with WebSocket frames appended in the
     * same byte buffer — for frame-before-upgrade testing.
     */
    public static byte[] buildUpgradeWithEarlyFrame(String host, int port,
                                                     String path, String origin,
                                                     String message) {
        String wsKey = generateWebSocketKey();
        byte[] upgrade = buildUpgradeRequest(host, port, path, origin, wsKey);
        byte[] frame = buildFrame(OP_TEXT, true, message.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[upgrade.length + frame.length];
        System.arraycopy(upgrade, 0, combined, 0, upgrade.length);
        System.arraycopy(frame, 0, combined, upgrade.length, frame.length);
        return combined;
    }

    /**
     * Build an HTTP Upgrade request that negotiates permessage-deflate.
     */
    public static byte[] buildDeflateUpgradeRequest(String host, int port,
                                                     String path, String origin,
                                                     String wsKey) {
        String request = "GET " + path + " HTTP/1.1\r\n" +
                "Host: " + host + ":" + port + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + wsKey + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "Sec-WebSocket-Extensions: permessage-deflate\r\n" +
                "Origin: " + origin + "\r\n" +
                "\r\n";
        return request.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Compress data using DEFLATE for permessage-deflate (RFC 7692).
     * Uses SYNC_FLUSH to produce a complete deflate block, then strips
     * the trailing 0x00 0x00 0xFF 0xFF per RFC 7692 Section 7.2.1.
     */
    public static byte[] deflateCompress(byte[] input) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true); // raw deflate, no zlib header
        deflater.setInput(input);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[Math.max(1024, input.length + 64)];

        // Use SYNC_FLUSH to produce complete deflate block
        int len = deflater.deflate(tmp, 0, tmp.length, Deflater.SYNC_FLUSH);
        buf.write(tmp, 0, len);
        deflater.end();

        byte[] compressed = buf.toByteArray();
        // Per RFC 7692 Section 7.2.1: remove trailing 0x00 0x00 0xFF 0xFF
        if (compressed.length >= 4 &&
                compressed[compressed.length - 4] == 0x00 &&
                compressed[compressed.length - 3] == 0x00 &&
                compressed[compressed.length - 2] == (byte) 0xFF &&
                compressed[compressed.length - 1] == (byte) 0xFF) {
            return java.util.Arrays.copyOf(compressed, compressed.length - 4);
        }
        return compressed;
    }

    /**
     * Build a masked frame with custom first byte (for RSV1 control).
     */
    private static byte[] buildMaskedFrame(int firstByte, byte[] payload) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(firstByte);

        int len = payload.length;
        if (len <= 125) {
            buf.write(0x80 | len);
        } else if (len <= 65535) {
            buf.write(0x80 | 126);
            buf.write((len >> 8) & 0xFF);
            buf.write(len & 0xFF);
        } else {
            buf.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) {
                buf.write((int) ((len >> (8 * i)) & 0xFF));
            }
        }

        byte[] mask = new byte[4];
        new Random().nextBytes(mask);
        buf.write(mask, 0, 4);

        for (int i = 0; i < payload.length; i++) {
            buf.write(payload[i] ^ mask[i % 4]);
        }

        return buf.toByteArray();
    }

    /**
     * Build a compressed WebSocket frame with RSV1 bit set (permessage-deflate).
     * RSV1=1 signals the message is compressed (set on first frame only).
     */
    public static byte[] buildCompressedFrame(int opcode, boolean fin, byte[] payload) {
        byte[] compressed = deflateCompress(payload);
        // FIN + RSV1=1 + opcode
        int b0 = (fin ? 0x80 : 0x00) | 0x40 | (opcode & 0x0F);
        return buildMaskedFrame(b0, compressed);
    }

    /**
     * Build a compressed continuation frame (no RSV1 — per RFC 7692,
     * only the first frame of a compressed message has RSV1 set).
     * Continuation frames carry raw (not separately compressed) data
     * that is part of the same deflate stream.
     */
    public static byte[] buildCompressedContinuationFrame(boolean fin, byte[] rawPayload) {
        // Continuation frames in permessage-deflate carry raw bytes that
        // are appended to the compressed stream. For our bomb, we send
        // uncompressed continuation frames — the server still buffers them.
        return buildFrame(OP_CONTINUATION, fin, rawPayload);
    }

    /**
     * Build a deflate + fragmentation bomb with real amplification.
     *
     * Strategy: Compress a large highly-compressible payload (all 'A's) as
     * a single deflate stream, then split the compressed bytes across many
     * small WebSocket frames. The first frame has RSV1=1 (compressed),
     * continuations carry subsequent chunks of the same deflate stream.
     *
     * The server decompresses the stream as fragments arrive. Each small
     * compressed chunk (tens of bytes on wire) decompresses to thousands
     * of bytes server-side — giving 100x-1000x amplification.
     *
     * No FIN=1 — server decompresses + buffers indefinitely.
     *
     * TC_RESET parallel:
     *   TC_RESET:  handleReset()  × N → CPU work before resolveClass()
     *   WS Deflate: inflate()     × N → CPU + memory before onMessage()
     *
     * @param fragmentCount number of frames to split the compressed data into
     * @param totalDecompressedSize total size after decompression (e.g. 10MB)
     * @return wire bytes (much smaller than totalDecompressedSize)
     */
    public static byte[] buildDeflateFragmentBomb(int fragmentCount, int totalDecompressedSize) {
        // Create highly compressible payload
        byte[] uncompressed = new byte[totalDecompressedSize];
        java.util.Arrays.fill(uncompressed, (byte) 'A');

        // Compress entire payload as one deflate stream
        byte[] compressed = deflateCompress(uncompressed);

        // Split compressed bytes across fragmentCount frames
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int chunkSize = Math.max(1, compressed.length / fragmentCount);
        int offset = 0;

        for (int i = 0; i < fragmentCount && offset < compressed.length; i++) {
            int end = Math.min(offset + chunkSize, compressed.length);
            byte[] chunk = java.util.Arrays.copyOfRange(compressed, offset, end);

            if (i == 0) {
                // First frame: text, RSV1=1, FIN=0
                int b0 = 0x40 | OP_TEXT; // RSV1=1, FIN=0
                byte[] frame = buildMaskedFrame(b0, chunk);
                buf.write(frame, 0, frame.length);
            } else {
                // Continuation: RSV1=0, FIN=0
                byte[] frame = buildFrame(OP_CONTINUATION, false, chunk);
                buf.write(frame, 0, frame.length);
            }
            offset = end;
        }

        // If there are remaining frames after compressed data is exhausted,
        // send empty continuation frames to reach fragmentCount
        for (int i = (compressed.length / Math.max(1, chunkSize)); i < fragmentCount; i++) {
            byte[] frame = buildFrame(OP_CONTINUATION, false, new byte[0]);
            buf.write(frame, 0, frame.length);
        }

        // NO FIN=1 — server decompresses all chunks, buffers result,
        // never delivers to onMessage()
        return buf.toByteArray();
    }

    /**
     * Payload sizes for systematic testing.
     */
    public enum Size {
        SMALL   (        100),
        MEDIUM  (     10_000),
        LARGE   (    100_000),
        EXTREME (  1_000_000);

        public final int count;

        Size(int n) {
            this.count = n;
        }
    }
}
