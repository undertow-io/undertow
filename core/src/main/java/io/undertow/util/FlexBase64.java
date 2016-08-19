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
package io.undertow.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

/**
 * An efficient and flexible Base64 implementation.
 *
 * This class can deal with both MIME Base64 and Base64url.
 *
 * @author Jason T. Greene
 */
public class FlexBase64 {
    /*
     * Note that this code heavily favors performance over reuse and clean style.
     */

    private static final byte[] STANDARD_ENCODING_TABLE;
    private static final byte[] STANDARD_DECODING_TABLE = new byte[80];
    private static final byte[] URL_ENCODING_TABLE;
    private static final byte[] URL_DECODING_TABLE = new byte[80];
    private static final Constructor<String> STRING_CONSTRUCTOR;

    static {
        STANDARD_ENCODING_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes(StandardCharsets.US_ASCII);
        URL_ENCODING_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".getBytes(StandardCharsets.US_ASCII);

        for (int i = 0; i < STANDARD_ENCODING_TABLE.length; i++) {
            int v = (STANDARD_ENCODING_TABLE[i] & 0xFF) - 43;
            STANDARD_DECODING_TABLE[v] = (byte)(i + 1);  // zero = illegal
        }

        for (int i = 0; i < URL_ENCODING_TABLE.length; i++) {
            int v = (URL_ENCODING_TABLE[i] & 0xFF) - 43;
            URL_DECODING_TABLE[v] = (byte)(i + 1);  // zero = illegal
        }


        Constructor<String> c = null;
        try {
            PrivilegedExceptionAction<Constructor<String>> runnable = new PrivilegedExceptionAction<Constructor<String>>() {
                @Override
                public Constructor<String> run() throws Exception {
                    Constructor<String> c;
                    c = String.class.getDeclaredConstructor(char[].class, boolean.class);
                    c.setAccessible(true);
                    return c;
                }
            };
            if (System.getSecurityManager() != null) {
                c = AccessController.doPrivileged(runnable);
            } else {
                c = runnable.run();
            }
        } catch (Throwable t) {
        }

        STRING_CONSTRUCTOR = c;
    }

    /**
     * Creates a state driven base64 encoder.
     *
     * <p>The Encoder instance is not thread-safe, and must not be shared between threads without establishing a
     * happens-before relationship.</p>
     *
     * @param wrap whether or not to wrap at 76 characters with CRLF
     * @return an createEncoder instance
     */
    public static Encoder createEncoder(boolean wrap) {
        return new Encoder(wrap, false);
    }


    /**
     * Creates a state driven base64url encoder.
     *
     * <p>The Encoder instance is not thread-safe, and must not be shared between threads without establishing a
     * happens-before relationship.</p>
     *
     * @param wrap whether or not to wrap at 76 characters with CRLF
     * @return an createEncoder instance
     */
    public static Encoder createURLEncoder(boolean wrap) {
        return new Encoder(wrap, true);
    }

    /**
     * Creates a state driven base64 decoder.
     *
     * <p>The Decoder instance is not thread-safe, and must not be shared between threads without establishing a
     * happens-before relationship.</p>
     *
     * @return a new createDecoder instance
     */
    public static Decoder createDecoder() {
        return new Decoder(false);
    }

    /**
     * Creates a state driven base64url decoder.
     *
     * <p>The Decoder instance is not thread-safe, and must not be shared between threads without establishing a
     * happens-before relationship.</p>
     *
     * @return a new createDecoder instance
     */
    public static Decoder createURLDecoder() {
        return new Decoder(true);
    }

    /**
     * Encodes a fixed and complete byte array into a Base64 String.
     *
     * <p>This method is only useful for applications which require a String and have all data to be encoded up-front.
     * Note that byte arrays or buffers are almost always a better storage choice. They consume half the memory and
     * can be reused (modified). In other words, it is almost always better to use {@link #encodeBytes},
     * {@link #createEncoder}, or {@link #createEncoderOutputStream} instead.
     * instead.
     *
     * @param source the byte array to encode from
     * @param wrap whether or not to wrap the output at 76 chars with CRLFs
     * @return a new String representing the Base64 output
     */
    public static String encodeString(byte[] source, boolean wrap) {
        return Encoder.encodeString(source, 0, source.length, wrap, false);
    }


    /**
     * Encodes a fixed and complete byte array into a Base64url String.
     *
     * <p>This method is only useful for applications which require a String and have all data to be encoded up-front.
     * Note that byte arrays or buffers are almost always a better storage choice. They consume half the memory and
     * can be reused (modified). In other words, it is almost always better to use {@link #encodeBytes},
     * {@link #createEncoder}, or {@link #createEncoderOutputStream} instead.
     * instead.
     *
     * @param source the byte array to encode from
     * @param wrap whether or not to wrap the output at 76 chars with CRLFs
     * @return a new String representing the Base64url output
     */
    public static String encodeStringURL(byte[] source, boolean wrap) {
        return Encoder.encodeString(source, 0, source.length, wrap, true);
    }

    /**
     * Encodes a fixed and complete byte array into a Base64 String.
     *
     * <p>This method is only useful for applications which require a String and have all data to be encoded up-front.
     * Note that byte arrays or buffers are almost always a better storage choice. They consume half the memory and
     * can be reused (modified). In other words, it is almost always better to use {@link #encodeBytes},
     * {@link #createEncoder}, or {@link #createEncoderOutputStream} instead.</p>
     *
     * <pre><code>
     *    // Encodes "ell"
     *    FlexBase64.encodeString("hello".getBytes("US-ASCII"), 1, 4);
     * </code></pre>
     *
     * @param source the byte array to encode from
     * @param pos the position to start encoding from
     * @param limit the position to halt encoding at (exclusive)
     * @param wrap whether or not to wrap the output at 76 chars with CRLFs
     * @return a new String representing the Base64 output
     */
    public static String encodeString(byte[] source, int pos, int limit, boolean wrap) {
        return Encoder.encodeString(source, pos, limit, wrap, false);
    }

    /**
     * Encodes a fixed and complete byte array into a Base64url String.
     *
     * <p>This method is only useful for applications which require a String and have all data to be encoded up-front.
     * Note that byte arrays or buffers are almost always a better storage choice. They consume half the memory and
     * can be reused (modified). In other words, it is almost always better to use {@link #encodeBytes},
     * {@link #createEncoder}, or {@link #createEncoderOutputStream} instead.</p>
     *
     * <pre><code>
     *    // Encodes "ell"
     *    FlexBase64.encodeStringURL("hello".getBytes("US-ASCII"), 1, 4);
     * </code></pre>
     *
     * @param source the byte array to encode from
     * @param pos the position to start encoding from
     * @param limit the position to halt encoding at (exclusive)
     * @param wrap whether or not to wrap the output at 76 chars with CRLFs
     * @return a new String representing the Base64url output
     */
    public static String encodeStringURL(byte[] source, int pos, int limit, boolean wrap) {
        return Encoder.encodeString(source, pos, limit, wrap, true);
    }
    /**
     * Encodes a fixed and complete byte buffer into a Base64 String.
     *
     * <p>This method is only useful for applications which require a String and have all data to be encoded up-front.
     * Note that byte arrays or buffers are almost always a better storage choice. They consume half the memory and
     * can be reused (modified). In other words, it is almost always better to use {@link #encodeBytes},
     * {@link #createEncoder}, or {@link #createEncoderOutputStream} instead.</p>
     *
     * <pre><code>
     *    // Encodes "ell"
     *    FlexBase64.ecncodeString("hello".getBytes("US-ASCII"), 1, 4);
     * </code></pre>
     *
     * @param source the byte buffer to encode from
     * @param wrap whether or not to wrap the output at 76 chars with CRLFs
     * @return a new String representing the Base64 output
     */
    public static String encodeString(ByteBuffer source, boolean wrap) {
        return Encoder.encodeString(source, wrap, false);
    }

    /**
     * Encodes a fixed and complete byte buffer into a Base64url String.
     *
     * <p>This method is only useful for applications which require a String and have all data to be encoded up-front.
     * Note that byte arrays or buffers are almost always a better storage choice. They consume half the memory and
     * can be reused (modified). In other words, it is almost always better to use {@link #encodeBytes},
     * {@link #createEncoder}, or {@link #createEncoderOutputStream} instead.</p>
     *
     * <pre><code>
     *    // Encodes "ell"
     *    FlexBase64.ecncodeStringURL("hello".getBytes("US-ASCII"), 1, 4);
     * </code></pre>
     *
     * @param source the byte buffer to encode from
     * @param wrap whether or not to wrap the output at 76 chars with CRLFs
     * @return a new String representing the Base64url output
     */
    public static String encodeStringURL(ByteBuffer source, boolean wrap) {
        return Encoder.encodeString(source, wrap, false);
    }

    /**
     * Encodes a fixed and complete byte buffer into a Base64 byte array.
     *
     * <pre><code>
     *    // Encodes "ell"
     *    FlexBase64.ecncodeString("hello".getBytes("US-ASCII"), 1, 4);
     * </code></pre>
     *
     * @param source the byte array to encode from
     * @param pos the position to start encoding at
     * @param limit the position to halt encoding at (exclusive)
     * @param wrap whether or not to wrap at 76 characters with CRLFs
     * @return a new byte array containing the encoded ASCII values
     */
    public static byte[] encodeBytes(byte[] source, int pos, int limit, boolean wrap) {
        return Encoder.encodeBytes(source, pos, limit, wrap, false);
    }

    /**
     * Encodes a fixed and complete byte buffer into a Base64url byte array.
     *
     * <pre><code>
     *    // Encodes "ell"
     *    FlexBase64.ecncodeStringURL("hello".getBytes("US-ASCII"), 1, 4);
     * </code></pre>
     *
     * @param source the byte array to encode from
     * @param pos the position to start encoding at
     * @param limit the position to halt encoding at (exclusive)
     * @param wrap whether or not to wrap at 76 characters with CRLFs
     * @return a new byte array containing the encoded ASCII values
     */
    public static byte[] encodeBytesURL(byte[] source, int pos, int limit, boolean wrap) {
        return Encoder.encodeBytes(source, pos, limit, wrap, true);
    }

    /**
     * Decodes a Base64 encoded string into a new byte buffer. The returned byte buffer is a heap buffer,
     * and it is therefor possible to retrieve the backing array using {@link java.nio.ByteBuffer#array()},
     * {@link java.nio.ByteBuffer#arrayOffset()} and {@link java.nio.ByteBuffer#limit()}. The latter is very
     * important since the decoded array may be larger than the decoded data. This is due to length estimation which
     * avoids an unnecessary array copy.
     *
     * @param source the Base64 string to decode
     * @return a byte buffer containing the decoded output
     * @throws IOException if the encoding is invalid or corrupted
     */
    public static ByteBuffer decode(String source) throws IOException {
        return Decoder.decode(source, false);
    }

    /**
     * Decodes a Base64url encoded string into a new byte buffer. The returned byte buffer is a heap buffer,
     * and it is therefor possible to retrieve the backing array using {@link java.nio.ByteBuffer#array()},
     * {@link java.nio.ByteBuffer#arrayOffset()} and {@link java.nio.ByteBuffer#limit()}. The latter is very
     * important since the decoded array may be larger than the decoded data. This is due to length estimation which
     * avoids an unnecessary array copy.
     *
     * @param source the Base64 string to decode
     * @return a byte buffer containing the decoded output
     * @throws IOException if the encoding is invalid or corrupted
     */
    public static ByteBuffer decodeURL(String source) throws IOException {
        return Decoder.decode(source, true);
    }

    /**
     * Decodes a Base64 encoded byte buffer into a new byte buffer. The returned byte buffer is a heap buffer,
     * and it is therefor possible to retrieve the backing array using {@link java.nio.ByteBuffer#array()},
     * {@link java.nio.ByteBuffer#arrayOffset()} and {@link java.nio.ByteBuffer#limit()}. The latter is very
     * important since the decoded array may be larger than the decoded data. This is due to length estimation which
     * avoids an unnecessary array copy.
     *
     * @param source the Base64 content to decode
     * @return a byte buffer containing the decoded output
     * @throws IOException if the encoding is invalid or corrupted
     */
    public static ByteBuffer decode(ByteBuffer source) throws IOException {
        return Decoder.decode(source, false);
    }


    /**
     * Decodes a Base64url encoded byte buffer into a new byte buffer. The returned byte buffer is a heap buffer,
     * and it is therefor possible to retrieve the backing array using {@link java.nio.ByteBuffer#array()},
     * {@link java.nio.ByteBuffer#arrayOffset()} and {@link java.nio.ByteBuffer#limit()}. The latter is very
     * important since the decoded array may be larger than the decoded data. This is due to length estimation which
     * avoids an unnecessary array copy.
     *
     * @param source the Base64 content to decode
     * @return a byte buffer containing the decoded output
     * @throws IOException if the encoding is invalid or corrupted
     */
    public static ByteBuffer decodeURL(ByteBuffer source) throws IOException {
        return Decoder.decode(source, true);
    }


    /**
     * Decodes a Base64 encoded byte array into a new byte buffer.  The returned byte buffer is a heap buffer,
     * and it is therefor possible to retrieve the backing array using {@link java.nio.ByteBuffer#array()},
     * {@link java.nio.ByteBuffer#arrayOffset()} and {@link java.nio.ByteBuffer#limit()}. The latter is very
     * important since the decoded array may be larger than the decoded data. This is due to length estimation which
     * avoids an unnecessary array copy.
     *
     * @param source the Base64 content to decode
     * @param off position to start decoding from in source
     * @param limit position to stop decoding in source (exclusive)
     * @return a byte buffer containing the decoded output
     * @throws IOException if the encoding is invalid or corrupted
     */
    public static ByteBuffer decode(byte[] source, int off, int limit) throws IOException {
        return Decoder.decode(source, off, limit, false);
    }

    /**
     * Decodes a Base64url encoded byte array into a new byte buffer.  The returned byte buffer is a heap buffer,
     * and it is therefor possible to retrieve the backing array using {@link java.nio.ByteBuffer#array()},
     * {@link java.nio.ByteBuffer#arrayOffset()} and {@link java.nio.ByteBuffer#limit()}. The latter is very
     * important since the decoded array may be larger than the decoded data. This is due to length estimation which
     * avoids an unnecessary array copy.
     *
     * @param source the Base64url content to decode
     * @param off position to start decoding from in source
     * @param limit position to stop decoding in source (exclusive)
     * @return a byte buffer containing the decoded output
     * @throws IOException if the encoding is invalid or corrupted
     */
    public static ByteBuffer decodeURL(byte[] source, int off, int limit) throws IOException {
        return Decoder.decode(source, off, limit, true);
    }

    /**
     * Creates an InputStream wrapper which encodes a source into base64 as it is read, until the source hits EOF.
     * Upon hitting EOF, a standard base64 termination sequence will be readable. Clients can simply treat this input
     * stream as if they were reading from a base64 encoded file. This stream attempts to read and encode in buffer
     * size chunks from the source, in order to improve overall performance. Thus, BufferInputStream is not necessary
     * and will lead to double buffering.
     *
     * <p>This stream is not thread-safe, and should not be shared between threads, without establishing a
     * happens-before relationship.</p>
     *
     * @param source an input source to read from
     * @param bufferSize the chunk size to buffer from the source
     * @param wrap whether or not the stream should wrap base64 output at 76 characters
     * @return an encoded input stream instance.
     */
    public static EncoderInputStream createEncoderInputStream(InputStream source, int bufferSize, boolean wrap) {
        return new EncoderInputStream(source, bufferSize, wrap, false);
    }


    /**
     * Creates an InputStream wrapper which encodes a source into base64 as it is read, until the source hits EOF.
     * Upon hitting EOF, a standard base64 termination sequence will be readable. Clients can simply treat this input
     * stream as if they were reading from a base64 encoded file. This stream attempts to read and encode in 8192 byte
     * chunks. Thus, BufferedInputStream is not necessary as a source and will lead to double buffering.
     *
     * <p>This stream is not thread-safe, and should not be shared between threads, without establishing a
     * happens-before relationship.</p>
     *
     * @param source an input source to read from
     * @return an encoded input stream instance.
     */
    public static EncoderInputStream createEncoderInputStream(InputStream source) {
        return new EncoderInputStream(source);
    }

    /**
     * Creates an InputStream wrapper which decodes a base64 input source into the decoded content as it is read,
     * until the source hits EOF. Upon hitting EOF, a standard base64 termination sequence will be readable.
     * Clients can simply treat this input stream as if they were reading from a base64 encoded file. This stream
     * attempts to read and encode in buffer size byte chunks. Thus, BufferedInputStream is not necessary
     * as a source and will lead to double buffering.
     *
     * <p>Note that the end of a base64 stream can not reliably be detected, so if multiple base64 streams exist on the
     * wire, the source stream will need to simulate an EOF when the boundary mechanism is detected.</p>
     *
     * <p>This stream is not thread-safe, and should not be shared between threads, without establishing a
     * happens-before relationship.</p>
     *
     * @param source an input source to read from
     * @param bufferSize the chunk size to buffer before when reading from the target
     * @return a decoded input stream instance.
     */
    public static DecoderInputStream createDecoderInputStream(InputStream source, int bufferSize) {
        return new DecoderInputStream(source, bufferSize);
    }


    /**
     * Creates an InputStream wrapper which decodes a base64 input source into the decoded content as it is read,
     * until the source hits EOF. Upon hitting EOF, a standard base64 termination sequence will be readable.
     * Clients can simply treat this input stream as if they were reading from a base64 encoded file. This stream
     * attempts to read and encode in 8192 byte chunks. Thus, BufferedInputStream is not necessary
     * as a source and will lead to double buffering.
     *
     * <p>Note that the end of a base64 stream can not reliably be detected, so if multiple base64 streams exist on the
     * wire, the source stream will need to simulate an EOF when the boundary mechanism is detected.</p>
     *
     * <p>This stream is not thread-safe, and should not be shared between threads, without establishing a
     * happens-before relationship.</p>
     *
     * @param source an input source to read from
     * @return a decoded input stream instance.
     */
    public static DecoderInputStream createDecoderInputStream(InputStream source) {
        return new DecoderInputStream(source);
    }

    /**
     * Creates an OutputStream wrapper which base64 encodes and writes to the passed OutputStream target. When this
     * stream is closed base64 padding will be added if needed. Alternatively if this represents an "inner stream",
     * the {@link FlexBase64.EncoderOutputStream#complete()}  method can be called to close out
     * the inner stream without closing the wrapped target.
     *
     * <p>All bytes written will be queued to a buffer in the specified size. This stream, therefore, does not require
     * BufferedOutputStream, which would lead to double buffering.
     *
     * @param target an output target to write to
     * @param bufferSize the chunk size to buffer before writing to the target
     * @param wrap whether or not the stream should wrap base64 output at 76 characters
     * @return an encoded output stream instance.
     */
    public static EncoderOutputStream createEncoderOutputStream(OutputStream target, int bufferSize, boolean wrap) {
        return new EncoderOutputStream(target, bufferSize, wrap);
    }


    /**
     * Creates an OutputStream wrapper which base64 encodes and writes to the passed OutputStream target. When this
     * stream is closed base64 padding will be added if needed. Alternatively if this represents an "inner stream",
     * the {@link FlexBase64.EncoderOutputStream#complete()}  method can be called to close out
     * the inner stream without closing the wrapped target.
     *
     * <p>All bytes written will be queued to an 8192 byte buffer. This stream, therefore, does not require
     * BufferedOutputStream, which would lead to double buffering.</p>
     *
     * <p>This stream is not thread-safe, and should not be shared between threads, without establishing a
     * happens-before relationship.</p>
     *
     * @param output the output stream to write encoded output to
     * @return an encoded output stream instance.
     */
    public static EncoderOutputStream createEncoderOutputStream(OutputStream output) {
        return new EncoderOutputStream(output);
    }


    /**
     * Creates an OutputStream wrapper which decodes base64 content before writing to the passed OutputStream target.
     *
     * <p>All bytes written will be queued to a buffer using the specified buffer size. This stream, therefore, does
     * not require BufferedOutputStream, which would lead to double buffering.</p>
     *
     * <p>This stream is not thread-safe, and should not be shared between threads, without establishing a
     * happens-before relationship.</p>
     *
     * @param output the output stream to write decoded output to
     * @param bufferSize the buffer size to buffer writes to
     * @return a decoded output stream instance.
     */
    public static DecoderOutputStream createDecoderOutputStream(OutputStream output, int bufferSize) {
        return new DecoderOutputStream(output, bufferSize);
    }

    /**
     * Creates an OutputStream wrapper which decodes base64 content before writing to the passed OutputStream target.
     *
     * <p>All bytes written will be queued to an 8192 byte buffer. This stream, therefore, does
     * not require BufferedOutputStream, which would lead to double buffering.</p>
     *
     * <p>This stream is not thread-safe, and should not be shared between threads, without establishing a
     * happens-before relationship.</p>
     *
     * @param output the output stream to write decoded output to
     * @return a decoded output stream instance.
     */
    public static DecoderOutputStream createDecoderOutputStream(OutputStream output) {
        return new DecoderOutputStream(output);
    }

    /**
     * Controls the encoding process.
     */
    public static final class Encoder {
        private int state;
        private int last;
        private int count;
        private final boolean wrap;
        private int lastPos;
        private final byte[] encodingTable;


        private Encoder(boolean wrap, boolean url) {
            this.wrap = wrap;
            this.encodingTable = url ? URL_ENCODING_TABLE : STANDARD_ENCODING_TABLE;
        }

        /**
         * Encodes bytes read from source and writes them in base64 format to target. If the source limit is hit, this
         * method will return and save the current state, such that future calls can resume the encoding process.
         * In addition, if the target does not have the capacity to fit an entire quad of bytes, this method will also
         * return and save state for subsequent calls to this method. Once all bytes have been encoded to the target,
         * {@link #complete(java.nio.ByteBuffer)} should be called to add the necessary padding characters.
         *
         * @param source the byte buffer to read from
         * @param target the byte buffer to write to
         */
        public void encode(ByteBuffer source, ByteBuffer target) {
            if (target == null)
                throw new IllegalStateException();

            int last = this.last;
            int state = this.state;
            boolean wrap = this.wrap;
            int count = this.count;
            final byte[] ENCODING_TABLE = encodingTable;

            int remaining = source.remaining();
            while (remaining > 0) {
                // Unrolled state machine for performance (resumes and executes all states in one iteration)
                int require = 4 - state;
                require = wrap && (count >= 72) ? require + 2 : require;
                if (target.remaining() < require) {
                    break;
                }
                //  ( 6 | 2) (4 | 4) (2 | 6)
                int b = source.get() & 0xFF;
                if (state == 0) {
                    target.put(ENCODING_TABLE[b >>> 2]);
                    last = (b & 0x3) << 4;
                    state++;
                    if (--remaining <= 0) {
                        break;
                    }
                    b = source.get() & 0xFF;
                }
                if (state == 1) {
                    target.put(ENCODING_TABLE[last | (b >>> 4)]);
                    last = (b & 0x0F) << 2;
                    state++;
                    if (--remaining <= 0) {
                        break;
                    }
                    b = source.get() & 0xFF;
                }
                if (state == 2) {
                    target.put(ENCODING_TABLE[last | (b >>> 6)]);
                    target.put(ENCODING_TABLE[b & 0x3F]);
                    last = state = 0;
                    remaining--;
                }

                if (wrap) {
                    count += 4;
                    if (count >= 76) {
                        count = 0;
                        target.putShort((short)0x0D0A);
                    }
                }
            }
            this.count = count;
            this.last = last;
            this.state = state;
            this.lastPos = source.position();
        }

        /**
         * Encodes bytes read from source and writes them in base64 format to target. If the source limit is hit, this
         * method will return and save the current state, such that future calls can resume the encoding process.
         * In addition, if the target does not have the capacity to fit an entire quad of bytes, this method will also
         * return and save state for subsequent calls to this method. Once all bytes have been encoded to the target,
         * {@link #complete(byte[], int)} should be called to add the necessary padding characters. In order to
         * determine the last read position, the {@link #getLastInputPosition()} can be used.
         *
         * <p>Note that the limit values are not lengths, they are positions similar to
         * {@link java.nio.ByteBuffer#limit()}. To calculate a length simply subtract position from limit.</p>
         *
         * <pre><code>
         *  Encoder encoder = FlexBase64.createEncoder(false);
         *  byte[] outBuffer = new byte[10];
         *  // Encode "ell"
         *  int outPosition = encoder.encode("hello".getBytes("US-ASCII"), 1, 4, outBuffer, 5, 10);
         *  // Prints "9 : ZWxs"
         *  System.out.println(outPosition + " : " + new String(outBuffer, 0, 5, outPosition - 5));
         * </code></pre>
         *
         * @param source the byte array to read from
         * @param pos ths position in the byte array to start reading from
         * @param limit the position in the byte array that is after the end of the source data
         * @param target the byte array to write base64 bytes to
         * @param opos the position to start writing to the target array at
         * @param olimit the position in the target byte array that makes the end of the writable area (exclusive)
         * @return the position in the target array immediately following the last byte written
         */
        public int encode(byte[] source, int pos, int limit, byte[] target, int opos, int olimit) {
            if (target == null)
                throw new IllegalStateException();

            int last = this.last;
            int state = this.state;
            int count = this.count;
            boolean wrap = this.wrap;
            final byte[] ENCODING_TABLE = encodingTable;


            while (limit > pos) {
                // Unrolled state machine for performance (resumes and executes all states in one iteration)
                int require = 4 - state;
                require = wrap && count >= 72 ? require + 2 : require;
                if ((require + opos) > olimit) {
                    break;
                }
                //  ( 6 | 2) (4 | 4) (2 | 6)
                int b = source[pos++] & 0xFF;
                if (state == 0) {
                    target[opos++] = ENCODING_TABLE[b >>> 2];
                    last = (b & 0x3) << 4;
                    state++;
                    if (pos >= limit) {
                        break;
                    }
                    b = source[pos++] & 0xFF;
                }
                if (state == 1) {
                    target[opos++] = ENCODING_TABLE[last | (b >>> 4)];
                    last = (b & 0x0F) << 2;
                    state++;
                    if (pos >= limit) {
                        break;
                    }
                    b = source[pos++] & 0xFF;
                }
                if (state == 2) {
                    target[opos++] = ENCODING_TABLE[last | (b >>> 6)];
                    target[opos++] = ENCODING_TABLE[b & 0x3F];

                    last = state = 0;
                }

                if (wrap) {
                    count += 4;
                    if (count >= 76) {
                        count = 0;
                        target[opos++] = 0x0D;
                        target[opos++] = 0x0A;
                    }
                }
            }
            this.count = count;
            this.last = last;
            this.state = state;
            this.lastPos = pos;

            return opos;
        }


        private static String encodeString(byte[] source, int pos, int limit, boolean wrap, boolean url) {
            int olimit = (limit - pos);
            int remainder = olimit % 3;
            olimit = (olimit + (remainder == 0 ? 0 : 3 - remainder)) / 3 * 4;
            olimit += (wrap ? (olimit / 76) * 2 + 2 : 0);
            char[] target = new char[olimit];
            int opos = 0;
            int last = 0;
            int count = 0;
            int state = 0;
            final byte[] ENCODING_TABLE = url ? URL_ENCODING_TABLE : STANDARD_ENCODING_TABLE;

            while (limit > pos) {
                //  ( 6 | 2) (4 | 4) (2 | 6)
                int b = source[pos++] & 0xFF;
                target[opos++] = (char) ENCODING_TABLE[b >>> 2];
                last = (b & 0x3) << 4;
                if (pos >= limit) {
                    state = 1;
                    break;
                }
                b = source[pos++] & 0xFF;
                target[opos++] = (char) ENCODING_TABLE[last | (b >>> 4)];
                last = (b & 0x0F) << 2;
                if (pos >= limit) {
                    state = 2;
                    break;
                }
                b = source[pos++] & 0xFF;
                target[opos++] = (char) ENCODING_TABLE[last | (b >>> 6)];
                target[opos++] = (char) ENCODING_TABLE[b & 0x3F];

                if (wrap) {
                    count += 4;
                    if (count >= 76) {
                        count = 0;
                        target[opos++] = 0x0D;
                        target[opos++] = 0x0A;
                    }
                }
            }

            complete(target, opos, state, last, wrap, url);

            try {
                // Eliminate copying on Open/Oracle JDK
                if (STRING_CONSTRUCTOR != null) {
                    return STRING_CONSTRUCTOR.newInstance(target, Boolean.TRUE);
                }
            } catch (Exception e) {
                // Ignoring on purpose
            }

            return new String(target);
        }

        private static byte[] encodeBytes(byte[] source, int pos, int limit, boolean wrap, boolean url) {
            int olimit = (limit - pos);
            int remainder = olimit % 3;
            olimit = (olimit + (remainder == 0 ? 0 : 3 - remainder)) / 3 * 4;
            olimit += (wrap ? (olimit / 76) * 2 + 2 : 0);
            byte[] target = new byte[olimit];
            int opos = 0;
            int count = 0;
            int last = 0;
            int state = 0;
            final byte[] ENCODING_TABLE = url ? URL_ENCODING_TABLE : STANDARD_ENCODING_TABLE;

            while (limit > pos) {
                //  ( 6 | 2) (4 | 4) (2 | 6)
                int b = source[pos++] & 0xFF;
                target[opos++] = ENCODING_TABLE[b >>> 2];
                last = (b & 0x3) << 4;
                if (pos >= limit) {
                    state = 1;
                    break;
                }
                b = source[pos++] & 0xFF;
                target[opos++] = ENCODING_TABLE[last | (b >>> 4)];
                last = (b & 0x0F) << 2;
                if (pos >= limit) {
                    state = 2;
                    break;
                }
                b = source[pos++] & 0xFF;
                target[opos++] = ENCODING_TABLE[last | (b >>> 6)];
                target[opos++] = ENCODING_TABLE[b & 0x3F];

                if (wrap) {
                    count += 4;
                    if (count >= 76) {
                        count = 0;
                        target[opos++] = 0x0D;
                        target[opos++] = 0x0A;
                    }
                }
            }

            complete(target, opos, state, last, wrap, url);

            return target;
        }

        private static String encodeString(ByteBuffer source, boolean wrap, boolean url) {
            int remaining = source.remaining();
            int remainder = remaining % 3;
            int olimit = (remaining + (remainder == 0 ? 0 : 3 - remainder)) / 3 * 4;
            olimit += (wrap ? olimit / 76 * 2 + 2 : 0);
            char[] target = new char[olimit];
            int opos = 0;
            int last = 0;
            int state = 0;
            int count = 0;
            final byte[] ENCODING_TABLE = url ? URL_ENCODING_TABLE : STANDARD_ENCODING_TABLE;


            while (remaining > 0) {
                //  ( 6 | 2) (4 | 4) (2 | 6)
                int b = source.get() & 0xFF;
                target[opos++] = (char) ENCODING_TABLE[b >>> 2];
                last = (b & 0x3) << 4;
                if (--remaining <= 0) {
                    state = 1;
                    break;
                }
                b = source.get() & 0xFF;
                target[opos++] = (char) ENCODING_TABLE[last | (b >>> 4)];
                last = (b & 0x0F) << 2;
                if (--remaining <= 0) {
                    state = 2;
                    break;
                }
                b = source.get() & 0xFF;
                target[opos++] = (char) ENCODING_TABLE[last | (b >>> 6)];
                target[opos++] = (char) ENCODING_TABLE[b & 0x3F];
                remaining--;

                if (wrap) {
                    count += 4;
                    if (count >= 76) {
                        count = 0;
                        target[opos++] = 0x0D;
                        target[opos++] = 0x0A;
                    }
                }
            }

            complete(target, opos, state, last, wrap, url);

            try {
                // Eliminate copying on Open/Oracle JDK
                if (STRING_CONSTRUCTOR != null) {
                    return STRING_CONSTRUCTOR.newInstance(target, Boolean.TRUE);
                }
            } catch (Exception e) {
                // Ignoring on purpose
            }

            return new String(target);
        }

        /**
         * Gets the last position where encoding left off in the last byte array that was used.
         * If the target for encoded content does not have the necessary capacity, this method should be used to
         * determine where to start from on subsequent reads.
         *
         * @return the last known read position
         */
        public int getLastInputPosition() {
            return lastPos;
        }

        /**
         * Completes an encoding session by writing out the necessary padding. This is essential to complying
         * with the Base64 format. This method will write at most 4 or 2 bytes starting at pos,depending on
         * whether or not wrapping is enabled.
         *
         * <pre><code>
         *  Encoder encoder = FlexBase64.createEncoder(false);
         *  byte[] outBuffer = new byte[13];
         *
         *  // Encodes "ello"
         *  int outPosition = encoder.encode("hello".getBytes("US-ASCII"), 0, 4, outBuffer, 5, 13);
         *  outPosition = encoder.complete(outBuffer, outPosition);
         *
         *  // Prints "13 : aGVsbA=="
         *  System.out.println(outPosition + " : " + new String(outBuffer, 0, 5, outPosition - 5));
         * </code></pre>
         *
         * @param target the byte array to write to
         * @param pos the position to start writing at
         * @return the position after the last byte written
         */
        public int complete(byte[] target, int pos) {
            if (state > 0) {
                target[pos++] = encodingTable[last];
                for (int i = state; i < 3; i++) {
                    target[pos++] = (byte)'=';
                }

                last = state = 0;
            }
            if (wrap) {
                target[pos++] = 0x0D;
                target[pos++] = 0x0A;
            }

            return pos;
        }

        private static int complete(char[] target, int pos, int state, int last, boolean wrap, boolean url) {
            if (state > 0) {
                target[pos++] = (char) (url ? URL_ENCODING_TABLE : STANDARD_ENCODING_TABLE)[last];
                for (int i = state; i < 3; i++) {
                    target[pos++] = '=';
                }
            }
            if (wrap) {
                target[pos++] = 0x0D;
                target[pos++] = 0x0A;
            }

            return pos;
        }

        private static int complete(byte[] target, int pos, int state, int last, boolean wrap, boolean url) {
            if (state > 0) {
                target[pos++] = (url ? URL_ENCODING_TABLE : STANDARD_ENCODING_TABLE)[last];
                for (int i = state; i < 3; i++) {
                    target[pos++] = '=';
                }
            }
            if (wrap) {
                target[pos++] = 0x0D;
                target[pos++] = 0x0A;
            }

            return pos;
        }

        /**
         * Completes an encoding session by writing out the necessary padding. This is essential to complying
         * with the Base64 format. This method will write at most 4 or 2 bytes, depending on whether or not wrapping
         * is enabled.
         *
         * @param target the byte buffer to write to
         */
        public void complete(ByteBuffer target) {
            if (state > 0) {
                target.put(encodingTable[last]);
                for (int i = state; i < 3; i++) {
                    target.put((byte)'=');
                }

                last = state = 0;
            }
            if (wrap) {
                target.putShort((short)0x0D0A);
            }

            count = 0;
        }
    }

    /**
     * Controls the decoding process.
     */
    public static final class Decoder {
        private int state;
        private int last;
        private int lastPos;
        private final byte[] decodingTable;

        private static final int SKIP = 0x0FD00;
        private static final int MARK = 0x0FE00;
        private static final int DONE = 0x0FF00;
        private static final int ERROR = 0xF0000;



        private Decoder(boolean url) {
            this.decodingTable = url ? URL_DECODING_TABLE : STANDARD_DECODING_TABLE;
        }


        private int nextByte(ByteBuffer buffer, int state, int last, boolean ignoreErrors) throws IOException {
            return nextByte(buffer.get() & 0xFF, state, last, ignoreErrors);
        }

        private int nextByte(Object source, int pos, int state, int last, boolean ignoreErrors) throws IOException {
            int c;
            if (source instanceof byte[]) {
                c = ((byte[])source)[pos] & 0xFF;
            } else if (source instanceof String) {
                c = ((String)source).charAt(pos) & 0xFF;
            } else {
                throw new IllegalArgumentException();
            }

            return nextByte(c, state, last, ignoreErrors);
        }

        private int nextByte(int c, int state, int last, boolean ignoreErrors) throws IOException {
            if (last == MARK) {
                if (c != '=') {
                    throw new IOException("Expected padding character");
                }
                return DONE;
            }
            if (c == '=') {
                if (state == 2) {
                    return MARK;
                } else if (state == 3) {
                    return DONE;
                } else {
                    throw new IOException("Unexpected padding character");
                }
            }
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                return SKIP;
            }
            if (c < 43 || c > 122) {
                if (ignoreErrors) {
                    return ERROR;
                }
                throw new IOException("Invalid base64 character encountered: " + c);
            }
            int b = (decodingTable[c - 43] & 0xFF) - 1;
            if (b < 0) {
                if (ignoreErrors) {
                    return ERROR;
                }
                throw new IOException("Invalid base64 character encountered: " + c);
            }
            return b;
        }

        /**
         * Decodes one Base64 byte buffer into another. This method will return and save state
         * if the target does not have the required capacity. Subsequent calls with a new target will
         * resume reading where it last left off (the source buffer's position). Similarly not all of the
         * source data need be available, this method can be repetitively called as data is made available.
         *
         * <p>The decoder will skip white space, but will error if it detects corruption.</p>
         *
         * @param source the byte buffer to read encoded data from
         * @param target the byte buffer to write decoded data to
         * @throws IOException if the encoded data is corrupted
         */
        public void decode(ByteBuffer source, ByteBuffer target) throws IOException {
            if (target == null)
                throw new IllegalStateException();

            int last = this.last;
            int state = this.state;

            int remaining = source.remaining();
            int targetRemaining = target.remaining();
            int b = 0;
            while (remaining-- > 0 && targetRemaining > 0) {
                b = nextByte(source, state, last, false);
                if (b == MARK) {
                    last = MARK;
                    if (--remaining <= 0) {
                        break;
                    }
                    b = nextByte(source, state, last, false);
                }
                if (b == DONE) {
                    last = state = 0;
                    break;
                }
                if (b == SKIP) {
                    continue;
                }
                //  ( 6 | 2) (4 | 4) (2 | 6)
                if (state == 0) {
                    last = b << 2;
                    state++;
                    if (remaining-- <= 0) {
                        break;
                    }
                    b = nextByte(source, state, last, false);
                    if ((b & 0xF000) != 0) {
                        source.position(source.position() - 1);
                        continue;
                    }
                }
                if (state == 1) {
                    target.put((byte)(last | (b >>> 4)));
                    last = (b & 0x0F) << 4;
                    state++;
                    if (remaining-- <= 0 || --targetRemaining <= 0) {
                        break;
                    }
                    b = nextByte(source, state, last, false);
                    if ((b & 0xF000) != 0) {
                        source.position(source.position() - 1);
                        continue;
                    }
                }
                if (state == 2) {
                    target.put((byte) (last | (b >>> 2)));
                    last = (b & 0x3) << 6;
                    state++;
                    if (remaining-- <= 0 || --targetRemaining <= 0) {
                        break;
                    }
                    b = nextByte(source, state, last, false);
                    if ((b & 0xF000) != 0) {
                        source.position(source.position() - 1);
                        continue;
                    }
                }
                if (state == 3) {
                    target.put((byte)(last | b));
                    last = state = 0;
                    targetRemaining--;
                }
            }

            if (remaining > 0) {
                drain(source, b, state, last);
            }

            this.last = last;
            this.state = state;
            this.lastPos = source.position();
        }

        private void drain(ByteBuffer source, int b, int state, int last) {
            while (b != DONE && source.remaining() > 0) {
                try {
                    b = nextByte(source, state, last, true);
                } catch (IOException e) {
                    b = 0;
                }

                if (b == MARK) {
                    last = MARK;
                    continue;
                }

                // Not WS/pad
                if ((b & 0xF000) == 0) {
                    source.position(source.position() - 1);
                    break;
                }
            }

            if (b == DONE) {
                // SKIP one line of trailing whitespace
                while (source.remaining() > 0) {
                    b = source.get();
                     if (b == '\n') {
                        break;
                    }  else if (b != ' ' && b != '\t' && b != '\r') {
                        source.position(source.position() - 1);
                        break;
                    }

                }
            }
        }

        private int drain(Object source, int pos, int limit, int b, int state, int last) {
            while (b != DONE && limit > pos) {
                try {
                    b = nextByte(source, pos++, state, last, true);
                } catch (IOException e) {
                    b = 0;
                }

                if (b == MARK) {
                    last = MARK;
                    continue;
                }

                // Not WS/pad
                if ((b & 0xF000) == 0) {
                    pos--;
                    break;
                }
            }

            if (b == DONE) {
                // SKIP one line of trailing whitespace
                while (limit > pos) {
                    if (source instanceof byte[]) {
                        b = ((byte[])source)[pos++] & 0xFF;
                    } else if (source instanceof String) {
                        b = ((String)source).charAt(pos++) & 0xFF;
                    } else {
                        throw new IllegalArgumentException();
                    }

                    if (b == '\n') {
                        break;
                    } else if (b != ' ' && b != '\t' && b != '\r') {
                        pos--;
                        break;
                    }


                }

            }


            return pos;
        }

        private int decode(Object source, int sourcePos, int sourceLimit, byte[] target, int targetPos, int targetLimit) throws IOException {
            if (target == null)
                throw new IllegalStateException();

            int last = this.last;
            int state = this.state;

            int pos = sourcePos;
            int opos = targetPos;
            int limit = sourceLimit;
            int olimit = targetLimit;

            int b = 0;
            while (limit > pos && olimit > opos) {
                b = nextByte(source, pos++, state, last, false);
                if (b == MARK) {
                    last = MARK;
                    if (pos >= limit) {
                        break;
                    }
                    b = nextByte(source, pos++, state, last, false);
                }
                if (b == DONE) {
                    last = state = 0;
                    break;
                }
                if (b == SKIP) {
                    continue;
                }
                //  ( 6 | 2) (4 | 4) (2 | 6)
                if (state == 0) {
                    last = b << 2;
                    state++;
                    if (pos >= limit) {
                        break;
                    }
                    b = nextByte(source, pos++, state, last, false);
                    if ((b & 0xF000) != 0) {
                        pos--;
                        continue;
                    }
                }
                if (state == 1) {
                    target[opos++] = ((byte)(last | (b >>> 4)));
                    last = (b & 0x0F) << 4;
                    state++;
                    if (pos >= limit || opos >= olimit) {
                        break;
                    }
                    b = nextByte(source, pos++, state, last, false);
                    if ((b & 0xF000) != 0) {
                        pos--;
                        continue;
                    }
                }
                if (state == 2) {
                    target[opos++] = ((byte) (last | (b >>> 2)));
                    last = (b & 0x3) << 6;
                    state++;
                    if (pos >= limit || opos >= olimit) {
                        break;
                    }
                    b = nextByte(source, pos++, state, last, false);
                    if ((b & 0xF000) != 0) {
                        pos--;
                        continue;
                    }
                }
                if (state == 3) {
                    target[opos++] = ((byte)(last | b));
                    last = state = 0;
                }
            }

            if (limit > pos) {
                pos = drain(source, pos, limit, b, state, last);
            }

            this.last = last;
            this.state = state;
            this.lastPos = pos;
            return opos;
        }

         /**
         * Gets the last position where decoding left off in the last byte array that was used for reading.
         * If the target for decoded content does not have the necessary capacity, this method should be used to
         * determine where to start from on subsequent decode calls.
         *
         * @return the last known read position
         */
        public int getLastInputPosition() {
            return lastPos;
        }


        /**
         * Decodes one Base64 byte array into another byte array. If the source limit is hit, this method will
         * return and save the current state, such that future calls can resume the decoding process. Likewise,
         * if the target does not have the capacity, this method will also return and save state for subsequent
         * calls to this method.
         *
         * <p>When multiple calls are made, {@link #getLastInputPosition()} should be used to determine what value
         * should be set for sourcePos. Likewise, the returned target position should be used as the targetPos
         * in a subsequent call.</p>
         *
         * <p>The decoder will skip white space, but will error if it detects corruption.</p>
         *
         * @param source a Base64 encoded string to decode data from
         * @param sourcePos the position in the source array to start decoding from
         * @param sourceLimit the position in the source array to halt decoding when hit (exclusive)
         * @param target the byte buffer to write decoded data to
         * @param targetPos the position in the target byte array to begin writing at
         * @param targetLimit  the position in the target byte array to halt writing (exclusive)
         * @throws IOException if the encoded data is corrupted
         * @return the position in the target array immediately following the last byte written
         *
         */
        public int decode(String source, int sourcePos, int sourceLimit, byte[] target, int targetPos, int targetLimit) throws IOException {
            return decode((Object)source, sourcePos, sourceLimit, target, targetPos, targetLimit);
        }

        /**
         * Decodes a Base64 encoded string into the passed byte array. This method will return and save state
         * if the target does not have the required capacity. Subsequent calls with a new target will
         * resume reading where it last left off (the source buffer's position). Similarly not all of the
         * source data need be available, this method can be repetitively called as data is made available.
         *
         * <p>Since this method variant assumes a position of 0 and a limit of the item length,
         * repeated calls will need fresh source and target values. {@link #decode(String, int, int, byte[], int, int)}
         * would be a better fit if you need reuse</p>
         *
         * <p>The decoder will skip white space, but will error if it detects corruption.</p>
         *
         * @param source a base64 encoded string to decode from
         * @param target a byte array to write to
         * @throws java.io.IOException if the base64 content is malformed
         * @return output position following the last written byte
         */
        public int decode(String source, byte[] target) throws IOException {
            return decode(source, 0, source.length(), target, 0, target.length);
        }

        /**
         * Decodes one Base64 byte array into another byte array. If the source limit is hit, this method will
         * return and save the current state, such that future calls can resume the decoding process. Likewise,
         * if the target does not have the capacity, this method will also return and save state for subsequent
         * calls to this method.
         *
         * <p>When multiple calls are made, {@link #getLastInputPosition()} should be used to determine what value
         * should be set for sourcePos. Likewise, the returned target position should be used as the targetPos
         * in a subsequent call.</p>
         *
         * <p>The decoder will skip white space, but will error if it detects corruption.</p>
         *
         * <pre><code>
         *  Decoder decoder = FlexBase64.createDecoder();
         *  byte[] outBuffer = new byte[10];
         *  byte[] bytes = "aGVsbG8=".getBytes("US-ASCII");
         *  // Decode only 2 bytes
         *  int outPosition = decoder.decode(bytes, 0, 8, outBuffer, 5, 7);
         *  // Resume where we left off and get the rest
         *  outPosition = decoder.decode(bytes, decoder.getLastInputPosition(), 8, outBuffer, outPosition, 10);
         *  // Prints "10 : Hello"
         *  System.out.println(outPosition + " : " + new String(outBuffer, 0, 5, outPosition - 5));
         * </code></pre>
         *
         *
         * @param source the byte array to read encoded data from
         * @param sourcePos the position in the source array to start decoding from
         * @param sourceLimit the position in the source array to halt decoding when hit (exclusive)
         * @param target the byte buffer to write decoded data to
         * @param targetPos the position in the target byte array to begin writing at
         * @param targetLimit the position in the target byte array to halt writing (exclusive)
         * @throws IOException if the encoded data is corrupted
         * @return the position in the target array immediately following the last byte written
         */
        public int decode(byte[] source, int sourcePos, int sourceLimit, byte[] target, int targetPos, int targetLimit) throws IOException {
            return decode((Object)source, sourcePos, sourceLimit, target, targetPos, targetLimit);
        }

        private static ByteBuffer decode(String source, boolean url) throws IOException {
            int remainder = source.length() % 4;
            int size = ((source.length() / 4) + (remainder == 0 ? 0 : 4 - remainder)) * 3;
            byte[] buffer = new byte[size];
            int actual = new Decoder(url).decode(source, 0, source.length(), buffer, 0, size);
            return ByteBuffer.wrap(buffer, 0, actual);
        }

        private static ByteBuffer decode(byte[] source, int off, int limit, boolean url) throws IOException {
            int len = limit - off;
            int remainder = len % 4;
            int size = ((len / 4) + (remainder == 0 ? 0 : 4 - remainder)) * 3;
            byte[] buffer = new byte[size];
            int actual = new Decoder(url).decode(source, off, limit, buffer, 0, size);
            return ByteBuffer.wrap(buffer, 0, actual);
        }

        private static ByteBuffer decode(ByteBuffer source, boolean url) throws IOException {
            int len = source.remaining();
            int remainder = len % 4;
            int size = ((len / 4) + (remainder == 0 ? 0 : 4 - remainder)) * 3;
            ByteBuffer buffer = ByteBuffer.allocate(size);
            new Decoder(url).decode(source, buffer);
            buffer.flip();
            return buffer;
        }

    }

    /**
     * An input stream which decodes bytes as they are read from a stream with Base64 encoded data.
     */
    public static class DecoderInputStream extends InputStream {
        private final InputStream input;
        private final byte[] buffer;
        private final Decoder decoder = createDecoder();
        private int pos = 0;
        private int limit = 0;
        private byte[] one;

        private DecoderInputStream(InputStream input) {
            this(input, 8192);
        }

        private DecoderInputStream(InputStream input, int bufferSize) {
            this.input = input;
            buffer = new byte[bufferSize];
        }

        private int fill() throws IOException {
            byte[] buffer = this.buffer;
            int read = input.read(buffer, 0, buffer.length);
            pos = 0;
            limit = read;
            return read;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            for (;;) {
                byte[] source = buffer;
                int pos = this.pos;
                int limit = this.limit;
                boolean setPos = true;

                if (pos >= limit) {
                    if (len > source.length) {
                        source = new byte[len];
                        limit = input.read(source, 0, len);
                        pos = 0;
                        setPos = false;
                    } else {
                        limit = fill();
                        pos = 0;
                    }

                    if (limit == -1) {
                        return -1;
                    }
                }

                int requested = len + pos;
                limit = limit > requested ? requested : limit;

                int read = decoder.decode(source, pos, limit, b, off, off+len) - off;
                if (setPos) {
                    this.pos = decoder.getLastInputPosition();
                }

                if (read > 0) {
                    return read;
                }
            }
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public int read() throws IOException {
            byte[] one = this.one;
            if (one == null) {
                one = this.one = new byte[1];
            }
            int read =  this.read(one, 0, 1);
            return read > 0 ? one[0] & 0xFF : -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() throws IOException {
            input.close();
        }
    }

    /**
     * An input stream which encodes bytes as they are read from a stream.
     */
    public static class EncoderInputStream extends InputStream {
        private final InputStream input;
        private final byte[] buffer;
        private final byte[] overflow = new byte[6];
        private int overflowPos;
        private int overflowLimit;
        private final Encoder encoder;
        private int pos = 0;
        private int limit = 0;
        private byte[] one;
        private boolean complete;

        private EncoderInputStream(InputStream input) {
            this(input, 8192, true, false);
        }

        private EncoderInputStream(InputStream input, int bufferSize, boolean wrap, boolean url) {
            this.input = input;
            buffer = new byte[bufferSize];
            this.encoder = new Encoder(wrap, url);
        }

        private int fill() throws IOException {
            byte[] buffer = this.buffer;
            int read = input.read(buffer, 0, buffer.length);
            pos = 0;
            limit = read;
            return read;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int read() throws IOException {
            byte[] one = this.one;
            if (one == null) {
                one = this.one = new byte[1];
            }
            int read =  this.read(one, 0, 1);
            return read > 0 ? one[0] & 0xFF : -1;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            byte[] buffer = this.buffer;
            byte[] overflow = this.overflow;
            int overflowPos = this.overflowPos;
            int overflowLimit = this.overflowLimit;
            boolean complete = this.complete;
            boolean wrap = encoder.wrap;

            int copy = 0;
            if (overflowPos < overflowLimit) {
                copy = copyOverflow(b, off, len, overflow, overflowPos, overflowLimit);
                if (len <= copy || complete) {
                    return copy;
                }

                len -= copy;
                off += copy;
            } else if (complete) {
                return -1;
            }

            for (;;) {
                byte[] source = buffer;
                int pos = this.pos;
                int limit = this.limit;
                boolean setPos = true;

                if (pos >= limit) {
                    if (len > source.length) {
                        // If requested length exceeds buffer, allocate a new temporary buffer that will be
                        // one block less than an exact encoded output. This is to handle partial quad carryover
                        // from an earlier read.
                        int adjust = (len / 4 * 3) - 3;
                        if (wrap) {
                            adjust -= adjust / 76 * 2 + 2;
                        }
                        source = new byte[adjust];
                        limit = input.read(source, 0, adjust);
                        pos = 0;
                        setPos = false;
                    } else {
                        limit = fill();
                        pos = 0;
                    }

                    if (limit <= 0) {
                        this.complete = true;

                        if (len < (wrap ? 4 : 2)) {
                            overflowLimit = encoder.complete(overflow, 0);
                            this.overflowLimit = overflowLimit;
                            int ret = copyOverflow(b, off, len, overflow, 0, overflowLimit) + copy;
                            return ret == 0 ? -1 : ret;
                        }

                        int ret = encoder.complete(b, off) - off + copy;
                        return ret == 0 ? -1 : ret;
                    }
                }

                if (len < (wrap ? 6 : 4)) {
                    overflowLimit = encoder.encode(source, pos, limit, overflow, 0, overflow.length);
                    this.overflowLimit = overflowLimit;
                    this.pos = encoder.getLastInputPosition();

                    return copyOverflow(b, off, len, overflow, 0, overflowLimit) + copy;
                }

                int read = encoder.encode(source, pos, limit, b, off, off+len) - off;
                if (setPos) {
                    this.pos = encoder.getLastInputPosition();
                }

                if (read > 0) {
                    return read + copy;
                }
            }
        }

        private int copyOverflow(byte[] b, int off, int len, byte[] overflow, int pos, int limit) {
            limit -= pos;
            len = limit <= len ? limit : len;
            System.arraycopy(overflow, pos, b, off, len);
            this.overflowPos = pos + len;
            return len;
        }
    }

    /**
     * An output stream which base64 encodes all passed data and writes it to the wrapped target output stream.
     *
     * <p>Closing this stream will result in the correct padding sequence being written. However, as
     * required by the OutputStream contract, the wrapped stream will also be closed. If this is not desired,
     * the {@link #complete()} method should be used.</p>
     */
    public static class EncoderOutputStream extends OutputStream {

        private final OutputStream output;
        private final byte[] buffer;
        private final Encoder encoder;
        private int pos = 0;
        private byte[] one;

        private EncoderOutputStream(OutputStream output) {
            this(output, 8192, true);
        }

        private EncoderOutputStream(OutputStream output, int bufferSize, boolean wrap) {
            this.output = output;
            this.buffer = new byte[bufferSize];
            this.encoder = createEncoder(wrap);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            byte[] buffer = this.buffer;
            Encoder encoder = this.encoder;
            int pos = this.pos;
            int limit = off + len;
            int ipos = off;

            while (ipos < limit) {
                pos = encoder.encode(b, ipos, limit, buffer, pos, buffer.length);
                int last = encoder.getLastInputPosition();
                if (last == ipos || pos >= buffer.length) {
                    output.write(buffer, 0, pos);
                    pos = 0;
                }
                ipos = last;
            }
            this.pos = pos;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void write(int b) throws IOException {
            byte[] one = this.one;
            if (one == null) {
                this.one = one = new byte[1];
            }

            one[0] = (byte)b;
            write(one, 0, 1);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void flush() throws IOException {
            OutputStream output = this.output;
            output.write(buffer, 0, pos);
            output.flush();
        }

        /**
         * Completes the stream, writing out base64 padding characters if needed.
         *
         * @throws IOException if the underlying stream throws one
         */
        public void complete() throws IOException {
            OutputStream output = this.output;
            byte[] buffer = this.buffer;
            int pos = this.pos;

            boolean completed = false;
            if (buffer.length - pos >= (encoder.wrap ? 2 : 4)) {
                this.pos = encoder.complete(buffer, pos);
                completed = true;
            }

            flush();

            if (!completed) {
                int len = encoder.complete(buffer, 0);
                output.write(buffer, 0, len);
                output.flush();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() throws IOException {
            try {
                complete();
            } catch (IOException e) {
                // eat
            }
            try {
                output.flush();
            } catch (IOException e) {
                // eat
            }
            output.close();
        }
    }

    /**
     * An output stream which decodes base64 data written to it, and writes the decoded output to the
     * wrapped inner stream.
     */
    public static class DecoderOutputStream extends OutputStream {

        private final OutputStream output;
        private final byte[] buffer;
        private final Decoder decoder;
        private int pos = 0;
        private byte[] one;

        private DecoderOutputStream(OutputStream output) {
            this(output, 8192);
        }

        private DecoderOutputStream(OutputStream output, int bufferSize) {
            this.output = output;
            this.buffer = new byte[bufferSize];
            this.decoder = createDecoder();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            byte[] buffer = this.buffer;
            Decoder decoder = this.decoder;
            int pos = this.pos;
            int limit = off + len;
            int ipos = off;

            while (ipos < limit) {
                pos = decoder.decode(b, ipos, limit, buffer, pos, buffer.length);
                int last = decoder.getLastInputPosition();
                if (last == ipos || pos >= buffer.length) {
                    output.write(buffer, 0, pos);
                    pos = 0;
                }
                ipos = last;
            }
            this.pos = pos;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void write(int b) throws IOException {
            byte[] one = this.one;
            if (one == null) {
                this.one = one = new byte[1];
            }

            one[0] = (byte)b;
            write(one, 0, 1);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void flush() throws IOException {
            OutputStream output = this.output;
            output.write(buffer, 0, pos);
            output.flush();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() throws IOException {
            try {
                flush();
            } catch (IOException e) {
                // eat
            }
            try {
                output.flush();
            } catch (IOException e) {
                // eat
            }
            output.close();
        }
    }
}
