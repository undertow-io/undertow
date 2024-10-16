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

package io.undertow.servlet.spec;

import javax.servlet.DispatcherType;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

/**
 * Real servlet print writer functionality, that is not limited by extending
 * {@link java.io.PrintWriter}
 * <p>
 *
 * @author Stuart Douglas
 */
public class ServletPrintWriter {

    private static final char[] EMPTY_CHAR = {};

    private final ServletOutputStreamImpl outputStream;
    private final String charset;
    private CharsetEncoder charsetEncoder;
    private boolean error = false;
    private boolean closed = false;
    private char[] underflow;

    public ServletPrintWriter(final ServletOutputStreamImpl outputStream, final String charset) throws UnsupportedEncodingException {
        this.charset = charset;
        this.outputStream = outputStream;

        //for some known charset we get optimistic and hope that
        //only ascii will be output
        //in this case we can avoid creating the encoder altogether
        if (!charset.equalsIgnoreCase("utf-8") &&
                !charset.equalsIgnoreCase("iso-8859-1")) {
            createEncoder();
        }
    }

    private void createEncoder() {
        this.charsetEncoder = Charset.forName(this.charset).newEncoder();
        //replace malformed and unmappable with question marks
        this.charsetEncoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        this.charsetEncoder.onMalformedInput(CodingErrorAction.REPLACE);
    }

    public void flush() {
        try {
            outputStream.flush();
        } catch (IOException e) {
            error = true;
        }
    }

    public void close() {

        if (outputStream.getServletRequestContext().getOriginalRequest().getDispatcherType() == DispatcherType.INCLUDE) {
            return;
        }
        if (closed) {
            return;
        }
        closed = true;
        try {
            boolean done = false;
            CharBuffer buffer;
            if (underflow == null) {
                buffer = CharBuffer.wrap(EMPTY_CHAR);
            } else {
                buffer = CharBuffer.wrap(underflow);
                underflow = null;
            }
            if (charsetEncoder != null) {
                int remaining = 0;
                do {
                    // before we get the underlying buffer, we need to flush outputStream
                    ByteBuffer out = outputStream.underlyingBuffer();
                    if (out == null) {
                        //servlet output stream has already been closed
                        error = true;
                        return;
                    }
                    CoderResult result = charsetEncoder.encode(buffer, out, true);
                    if (result.isOverflow()) {
                        outputStream.flushInternal();
                        if (out.remaining() == remaining) {
                            // no progress in flush
                            outputStream.close();
                            error = true;
                            return;
                        } else
                            remaining = out.remaining();
                    } else {
                        done = true;
                    }
                } while (!done);
            }
            outputStream.close();
        } catch (IOException e) {
            error = true;
        }
    }

    public boolean checkError() {
        flush();
        return error;
    }

    public void write(final CharBuffer input) {
        ByteBuffer buffer = outputStream.underlyingBuffer();
        if (buffer == null) {
            //stream has been closed
            error = true;
            return;
        }
        try {
            if (!buffer.hasRemaining()) {
                outputStream.flushInternal();
                if (!buffer.hasRemaining()) {
                    error = true;
                    return;
                }
            }

            if (charsetEncoder == null) {
                createEncoder();
            }
            final CharBuffer cb;
            if (underflow == null) {
                cb = input;
            } else {
                char[] newArray = new char[underflow.length + input.remaining()];
                System.arraycopy(underflow, 0, newArray, 0, underflow.length);
                input.get(newArray, underflow.length, input.remaining());
                cb = CharBuffer.wrap(newArray);
                underflow = null;
            }
            int last = -1;
            long remainingContentLength = outputStream.remainingContentLength();
            while (remainingContentLength > 0 && cb.hasRemaining()) {
                final int remaining = buffer.remaining();
                final CoderResult result = charsetEncoder.encode(cb, buffer, false);
                long writtenLength = remaining - buffer.remaining();
                if (remainingContentLength < writtenLength) {
                    buffer.position(buffer.position() - (int) (writtenLength - remainingContentLength));
                    writtenLength = remainingContentLength;
                }
                remainingContentLength -= writtenLength;
                outputStream.updateWritten(writtenLength);
                if (result.isOverflow() || !buffer.hasRemaining()) {
                    final int remainingBytesBeforeFlush = buffer.remaining();
                    outputStream.flushInternal();
                    if (buffer.remaining() == remainingBytesBeforeFlush) {
                        // no progress has been made, set error to true
                        error = true;
                        return;
                    }
                }
                if (result.isUnderflow()) {
                    underflow = new char[cb.remaining()];
                    cb.get(underflow);
                    return;
                }
                if (result.isError()) {
                    error = true;
                    return;
                }
                if (result.isUnmappable()) {
                    //this should not happen
                    error = true;
                    return;
                }
                if (last == cb.remaining()) {
                    underflow = new char[cb.remaining()];
                    cb.get(underflow);
                    return;
                }
                last = cb.remaining();
            }
        } catch (IOException e) {
            error = true;
        }
    }

    public void write(final int c) {
        write(Character.toString((char) c));
    }

    public void write(final char[] buf, final int off, final int len) {
        if (charsetEncoder == null) {
            try {
                ByteBuffer buffer = outputStream.underlyingBuffer();
                if (buffer == null) {
                    //already closed
                    error = true;
                    return;
                }
                //fast path, basically we are hoping this is ascii only
                int remaining = buffer.remaining();
                int end = off + len;
                //so we have a pure ascii buffer, just write it out and skip all the encoder cost
                final int sPos = writeAndFlushAscii(outputStream, buffer, buf, off, off + len);
                outputStream.updateWritten(remaining - buffer.remaining());
                if (sPos == end) {
                    return;
                }
                final CharBuffer cb = CharBuffer.wrap(buf, sPos, len - (sPos - off));
                write(cb);
                return;
            } catch (IOException e) {
                error = false;
                return;
            }

        }
        final CharBuffer cb = CharBuffer.wrap(buf, off, len);
        write(cb);
    }

    private static int writeAndFlushAscii(ServletOutputStreamImpl outputStream, ByteBuffer buffer, char[] chars, int start, int end) throws IOException {
        assert buffer.order() == ByteOrder.BIG_ENDIAN;
        int i = start;
        long remainingContentLength = outputStream.remainingContentLength();
        while (i < end) {
            final int bufferPos = buffer.position();
            final int bufferRemaining = buffer.remaining();
            final int sRemaining = end - i;
            final int remaining = Math.min(sRemaining, bufferRemaining);
            final int written = setAsciiBE(buffer, bufferPos, chars, i, remaining);
            i += written;
            if ((remainingContentLength -= written) < 0) {
                buffer.position(bufferPos + written + (int) remainingContentLength);
            } else {
                buffer.position(bufferPos + written);
            }
            if (!buffer.hasRemaining()) {
                outputStream.flushInternal();
            }
            // we have given up with the fast path? slow path NOW!
            if (written < remaining) {
                return i;
            }
        }
        return i;
    }

    private static int setAsciiBE(ByteBuffer buffer, int out, char[] chars, int off, int len) {
        final int longRounds = len >>> 3;
        for (int i = 0; i < longRounds; i++) {
            final long batch1 = (((long) chars[off]) << 48) |
                    (((long) chars[off + 2]) << 32) |
                    chars[off + 4] << 16 |
                    chars[off + 6];
            final long batch2 = (((long) chars[off + 1]) << 48) |
                    (((long) chars[off + 3]) << 32) |
                    chars[off + 5] << 16 |
                    chars[off + 7];
            if (((batch1 | batch2) & 0xff80_ff80_ff80_ff80L) != 0) {
                return i << 3;
            }
            final long batch = (batch1 << 8) | batch2;
            buffer.putLong(out, batch);
            out += Long.BYTES;
            off += Long.BYTES;
        }
        final int byteRounds = len & 7;
        if (byteRounds > 0) {
            for (int i = 0; i < byteRounds; i++) {
                final char c = chars[off + i];
                if (c > 127) {
                    return (longRounds << 3) + i;
                }
                buffer.put(out + i, (byte) c);
            }
        }
        return len;
    }

    public void write(final char[] buf) {
        write(buf, 0, buf.length);
    }

    public void write(final String s, final int off, final int len) {
        if (charsetEncoder == null) {
            try {
                ByteBuffer buffer = outputStream.underlyingBuffer();
                if (buffer == null) {
                    //already closed
                    error = true;
                    return;
                }
                //fast path, basically we are hoping this is ascii only
                int remaining = buffer.remaining();
                int end = off + len;
                //so we have a pure ascii buffer, just write it out and skip all the encoder cost
                final int sPos = writeAndFlushAscii(outputStream, buffer, s, off, off + len);
                outputStream.updateWritten(remaining - buffer.remaining());
                if (sPos == end) {
                    return;
                }
                //wrap(String, off, len) acts wrong in the presence of multi byte characters
                final CharBuffer cb = CharBuffer.wrap(s.toCharArray(), sPos, len - (sPos - off));
                write(cb);
                return;
            } catch (IOException e) {
                error = false;
                return;
            }

        }
        final CharBuffer cb = CharBuffer.wrap(s, off, off + len);
        write(cb);
    }

    private static int writeAndFlushAscii(ServletOutputStreamImpl outputStream, ByteBuffer buffer, String s, int start, int end) throws IOException {
        assert buffer.order() == ByteOrder.BIG_ENDIAN;
        int i = start;
        long remainingContentLength = outputStream.remainingContentLength();
        while (i < end) {
            final int bufferPos = buffer.position();
            final int bufferRemaining = buffer.remaining();
            final int sRemaining = end - i;
            final int remaining = Math.min(sRemaining, bufferRemaining);
            final int written = setAsciiBE(buffer, bufferPos, s, i, remaining);
            i += written;
            if ((remainingContentLength -= written) < 0) {
                buffer.position(bufferPos + written + (int) remainingContentLength);
            } else {
                buffer.position(bufferPos + written);
            }
            if (!buffer.hasRemaining()) {
                outputStream.flushInternal();
            }
            // we have given up with the fast path? slow path NOW!
            if (written < remaining) {
                return i;
            }
        }
        return i;
    }

    private static int setAsciiBE(ByteBuffer buffer, int out, String s, int off, int len) {
        final int longRounds = len >>> 3;
        for (int i = 0; i < longRounds; i++) {
            final long batch1 = ((long)s.charAt(off)) << 48 |
                    ((long) s.charAt(off + 2)) << 32 |
                    s.charAt(off + 4) << 16 |
                    s.charAt(off + 6);
            final long batch2 = ((long)s.charAt(off + 1)) << 48 |
                    ((long) s.charAt(off + 3)) << 32 |
                    s.charAt(off + 5) << 16 |
                    s.charAt(off + 7);
            if (((batch1 | batch2) & 0xff80_ff80_ff80_ff80L) != 0) {
                return i << 3;
            }
            final long batch = (batch1 << 8) | batch2;
            buffer.putLong(out, batch);
            out += Long.BYTES;
            off += Long.BYTES;
        }
        final int byteRounds = len & 7;
        if (byteRounds > 0) {
            for (int i = 0; i < byteRounds; i++) {
                final char c = s.charAt(off + i);
                if (c > 127) {
                    return (longRounds << 3) + i;
                }
                buffer.put(out + i, (byte) c);
            }
        }
        return len;
    }

    public void write(final String s) {
        write(s, 0, s.length());
    }

    public void print(final boolean b) {
        write(Boolean.toString(b));
    }

    public void print(final char c) {
        write(Character.toString(c));
    }

    public void print(final int i) {
        write(Integer.toString(i));
    }

    public void print(final long l) {
        write(Long.toString(l));
    }

    public void print(final float f) {
        write(Float.toString(f));
    }

    public void print(final double d) {
        write(Double.toString(d));
    }

    public void print(final char[] s) {
        write(CharBuffer.wrap(s));
    }

    public void print(final String s) {
        write(s == null ? "null" : s);
    }

    public void print(final Object obj) {
        write(obj == null ? "null" : obj.toString());
    }

    public void println() {
        print("\r\n");
    }

    public void println(final boolean b) {
        print(b);
        println();
    }

    public void println(final char c) {
        print(c);
        println();
    }

    public void println(final int i) {
        print(i);
        println();
    }

    public void println(final long l) {
        print(l);
        println();
    }

    public void println(final float f) {
        print(f);
        println();
    }

    public void println(final double d) {
        print(d);
        println();
    }

    public void println(final char[] s) {
        print(s);
        println();
    }

    public void println(final String s) {
        print(s);
        println();
    }

    public void println(final Object obj) {
        print(obj);
        println();
    }

    public void printf(final String format, final Object... args) {
        print(String.format(format, args));
    }

    public void printf(final Locale l, final String format, final Object... args) {
        print(String.format(l, format, args));
    }


    public void format(final String format, final Object... args) {
        printf(format, args);
    }

    public void format(final Locale l, final String format, final Object... args) {
        printf(l, format, args);
    }

    public void append(final CharSequence csq) {
        if (csq == null) {
            write("null");
        } else {
            write(csq.toString());
        }
    }

    public void append(final CharSequence csq, final int start, final int end) {
        CharSequence cs = (csq == null ? "null" : csq);
        write(cs.subSequence(start, end).toString());
    }

    public void append(final char c) {
        write(c);
    }

}
