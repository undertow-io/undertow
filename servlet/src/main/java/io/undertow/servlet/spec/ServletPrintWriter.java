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

import jakarta.servlet.DispatcherType;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.UnsupportedCharsetException;
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

    private void createEncoder() throws UnsupportedEncodingException {
        try {
            this.charsetEncoder = Charset.forName(this.charset).newEncoder();
            //replace malformed and unmappable with question marks
            this.charsetEncoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
            this.charsetEncoder.onMalformedInput(CodingErrorAction.REPLACE);
        } catch (UnsupportedCharsetException e) {
            // Servlet 6 clarification - UnsupportedEncodingException must be thrown
            final UnsupportedEncodingException uee = new UnsupportedEncodingException(e.getMessage());
            uee.initCause(e);
            throw uee;
        }
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
                do {
                    ByteBuffer out = outputStream.underlyingBuffer();
                    if (out == null) {
                        //servlet output stream has already been closed
                        error = true;
                        return;
                    }
                    CoderResult result = charsetEncoder.encode(buffer, out, true);
                    if (result.isOverflow()) {
                        outputStream.flushInternal();
                        if (out.remaining() == 0) {
                            outputStream.close();
                            error = true;
                            return;
                        }
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
            while (cb.hasRemaining()) {
                int remaining = buffer.remaining();
                CoderResult result = charsetEncoder.encode(cb, buffer, false);
                outputStream.updateWritten(remaining - buffer.remaining());
                if (result.isOverflow() || !buffer.hasRemaining()) {
                    outputStream.flushInternal();
                    if (!buffer.hasRemaining()) {
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
        write(Character.toString((char)c));
    }

    public void write(final char[] buf, final int off, final int len) {
        if(charsetEncoder == null) {
            try {
                ByteBuffer buffer = outputStream.underlyingBuffer();
                if(buffer == null) {
                    //already closed
                    error = true;
                    return;
                }
                //fast path, basically we are hoping this is ascii only
                int remaining = buffer.remaining();
                boolean ok = true;
                //so we have a pure ascii buffer, just write it out and skip all the encoder cost

                int end = off + len;
                int i = off;
                int flushPos = i + remaining;
                while (ok && i < end) {
                    int realEnd = Math.min(end, flushPos);
                    for (; i < realEnd; ++i) {
                        char c = buf[i];
                        if (c > 127) {
                            ok = false;
                            break;
                        } else {
                            buffer.put((byte) c);
                        }
                    }
                    if (i == flushPos) {
                        outputStream.flushInternal();
                        flushPos = i + buffer.remaining();
                    }
                }
                outputStream.updateWritten(remaining - buffer.remaining());
                if (ok) {
                    return;
                }
                final CharBuffer cb = CharBuffer.wrap(buf, i, len - (i - off));
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

    public void write(final char[] buf) {
        write(buf,0, buf.length);
    }

    public void write(final String s, final int off, final int len) {
        if(charsetEncoder == null) {
            try {
                ByteBuffer buffer = outputStream.underlyingBuffer();
                if(buffer == null) {
                    //already closed
                    error = true;
                    return;
                }
                //fast path, basically we are hoping this is ascii only
                int remaining = buffer.remaining();
                boolean ok = true;
                //so we have a pure ascii buffer, just write it out and skip all the encoder cost

                int end = off + len;
                int i = off;
                int fpos = i + remaining;
                for (; i < end; ++i) {
                    if (i == fpos) {
                        outputStream.flushInternal();
                        fpos = i + buffer.remaining();
                    }
                    char c = s.charAt(i);
                    if (c > 127) {
                        ok = false;
                        break;
                    }
                    buffer.put((byte) c);
                }
                outputStream.updateWritten(remaining - buffer.remaining());
                if (ok) {
                    return;
                }
                //wrap(String, off, len) acts wrong in the presence of multi byte characters
                final CharBuffer cb = CharBuffer.wrap(s.toCharArray(), i, len - (i - off));
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
