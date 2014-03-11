package io.undertow.servlet.spec;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

/**
 * Real servlet print writer functionality, that is not limited by extending
 * {@link java.io.PrintWriter}
 * <p/>
 *
 * @author Stuart Douglas
 */
public class ServletPrintWriter {

    private static final char[] EMPTY_CHAR = {};

    private final ServletOutputStreamImpl outputStream;
    private final CharsetEncoder charsetEncoder;
    private boolean error = false;
    private char[] underflow;

    public ServletPrintWriter(final ServletOutputStreamImpl outputStream, final String charset) throws UnsupportedEncodingException {
        this.outputStream = outputStream;
        this.charsetEncoder = Charset.forName(charset).newEncoder();
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
        try {
            boolean done = false;
            do {
                CoderResult result = charsetEncoder.encode(CharBuffer.wrap(EMPTY_CHAR), outputStream.underlyingBuffer(), true);
                if (result.isOverflow()) {
                    outputStream.flushInternal();
                } else {
                    done = true;
                }
            } while (!done);
            outputStream.close();
        } catch (IOException e) {
            error = true;
        }
    }

    public boolean checkError() {
        return error;
    }

    public void write(final CharBuffer input) {
        ByteBuffer buffer = outputStream.underlyingBuffer();
        try {
            if (!buffer.hasRemaining()) {
                outputStream.flushInternal();
            }
            final CharBuffer cb;
            if(underflow == null) {
                cb = input;
            } else {
                char[] newArray = new char[underflow.length + input.remaining()];
                System.arraycopy(underflow, 0, newArray, 0, underflow.length);
                input.get(newArray, underflow.length, input.remaining());
                cb = CharBuffer.wrap(newArray);
                underflow = null;
            }
            while (cb.hasRemaining()) {
                int remaining = buffer.remaining();
                CoderResult result = charsetEncoder.encode(cb, buffer, false);
                outputStream.updateWritten(remaining - buffer.remaining());
                if (result.isOverflow() || !buffer.hasRemaining()) {
                    outputStream.flushInternal();
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
            }
        } catch (IOException e) {
            error = true;
        }
    }

    public void write(final int c) {
        final CharBuffer cb = CharBuffer.wrap(Character.toString((char) c));
        write(cb);
    }

    public void write(final char[] buf, final int off, final int len) {
        final CharBuffer cb = CharBuffer.wrap(buf, off, len);
        write(cb);
    }

    public void write(final char[] buf) {
        final CharBuffer cb = CharBuffer.wrap(buf);
        write(cb);
    }

    public void write(final String s, final int off, final int len) {
        final CharBuffer cb = CharBuffer.wrap(s, off, off + len);
        write(cb);
    }

    public void write(final String s) {
        final CharBuffer cb = CharBuffer.wrap(s);
        write(cb);
    }

    public void print(final boolean b) {
        final CharBuffer cb = CharBuffer.wrap(Boolean.toString(b));
        write(cb);
    }

    public void print(final char c) {
        final CharBuffer cb = CharBuffer.wrap(Character.toString(c));
        write(cb);
    }

    public void print(final int i) {
        final CharBuffer cb = CharBuffer.wrap(Integer.toString(i));
        write(cb);
    }

    public void print(final long l) {
        final CharBuffer cb = CharBuffer.wrap(Long.toString(l));
        write(cb);
    }

    public void print(final float f) {
        final CharBuffer cb = CharBuffer.wrap(Float.toString(f));
        write(cb);
    }

    public void print(final double d) {
        final CharBuffer cb = CharBuffer.wrap(Double.toString(d));
        write(cb);
    }

    public void print(final char[] s) {
        final CharBuffer cb = CharBuffer.wrap(s);
        write(cb);
    }

    public void print(final String s) {
        final CharBuffer cb = CharBuffer.wrap(s == null ? "null" : s);
        write(cb);
    }

    public void print(final Object obj) {
        final CharBuffer cb = CharBuffer.wrap(obj == null ? "null" : obj.toString());
        write(cb);
    }

    public void println() {
        print('\n');
    }

    public void println(final boolean b) {
        print(b);
        print('\n');
    }

    public void println(final char c) {
        print(c);
        print('\n');
    }

    public void println(final int i) {
        print(i);
        print('\n');
    }

    public void println(final long l) {
        print(l);
        print('\n');
    }

    public void println(final float f) {
        print(f);
        print('\n');
    }

    public void println(final double d) {
        print(d);
        print('\n');
    }

    public void println(final char[] s) {
        print(s);
        print('\n');
    }

    public void println(final String s) {
        print(s);
        print('\n');
    }

    public void println(final Object obj) {
        print(obj);
        print('\n');
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
        if (csq == null)
            write("null");
        else
            write(csq.toString());
    }

    public void append(final CharSequence csq, final int start, final int end) {
        CharSequence cs = (csq == null ? "null" : csq);
        write(cs.subSequence(start, end).toString());
    }

    public void append(final char c) {
        write(c);
    }

}
