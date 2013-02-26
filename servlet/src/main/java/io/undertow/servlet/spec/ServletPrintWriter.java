package io.undertow.servlet.spec;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.Locale;

/**
 * Real servlet print writer functionality, that is not limited by extending
 * {@link java.io.PrintWriter}
 * <p/>
 *
 * @author Stuart Douglas
 */
public class ServletPrintWriter {

    private final ServletOutputStreamImpl outputStream;
    private final CharsetEncoder charsetEncoder;
    private boolean error = false;

    public ServletPrintWriter(final ServletOutputStreamImpl outputStream, final String charset) throws UnsupportedEncodingException {
        this.outputStream = outputStream;
        this.charsetEncoder = Charset.forName(charset).newEncoder();
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
            outputStream.close();
        } catch (IOException e) {
            error = true;
        }
    }

    public boolean checkError() {
        return error;
    }

    private void doWrite(final CharBuffer cb) {
        ByteBuffer buffer = outputStream.underlyingBuffer();
        try {
            if (!buffer.hasRemaining()) {
                outputStream.flush();
            }
            while (cb.hasRemaining()) {
                int remaining = buffer.remaining();
                CoderResult result = charsetEncoder.encode(cb, buffer, false);
                outputStream.updateWritten(remaining - buffer.remaining());
                if(result.isOverflow()) {
                    outputStream.flush();
                }
            }
        } catch (IOException e) {
            error = true;
        }
    }

    public void write(final int c) {
        final CharBuffer cb = CharBuffer.wrap(Character.toString((char)c));
        doWrite(cb);
    }

    public void write(final char[] buf, final int off, final int len) {
        final CharBuffer cb = CharBuffer.wrap(buf, off, len);
        doWrite(cb);
    }

    public void write(final char[] buf) {
        final CharBuffer cb = CharBuffer.wrap(buf);
        doWrite(cb);
    }

    public void write(final String s, final int off, final int len) {
        final CharBuffer cb = CharBuffer.wrap(s, off, len);
        doWrite(cb);
    }

    public void write(final String s) {
        final CharBuffer cb = CharBuffer.wrap(s);
        doWrite(cb);
    }

    public void print(final boolean b) {
        final CharBuffer cb = CharBuffer.wrap(Boolean.toString(b));
        doWrite(cb);
    }

    public void print(final char c) {
        final CharBuffer cb = CharBuffer.wrap(Character.toString(c));
        doWrite(cb);
    }

    public void print(final int i) {
        final CharBuffer cb = CharBuffer.wrap(Integer.toString(i));
        doWrite(cb);
    }

    public void print(final long l) {
        final CharBuffer cb = CharBuffer.wrap(Long.toString(l));
        doWrite(cb);
    }

    public void print(final float f) {
        final CharBuffer cb = CharBuffer.wrap(Float.toString(f));
        doWrite(cb);
    }

    public void print(final double d) {
        final CharBuffer cb = CharBuffer.wrap(Double.toString(d));
        doWrite(cb);
    }

    public void print(final char[] s) {
        final CharBuffer cb = CharBuffer.wrap(s);
        doWrite(cb);
    }

    public void print(final String s) {
        final CharBuffer cb = CharBuffer.wrap(s);
        doWrite(cb);
    }

    public void print(final Object obj) {
        final CharBuffer cb = CharBuffer.wrap(obj == null ? "null" : obj.toString());
        doWrite(cb);
    }

    public void println() {
        final CharBuffer cb = CharBuffer.wrap("\n");
        doWrite(cb);
    }

    public void println(final boolean b) {
        final CharBuffer cb = CharBuffer.wrap(Boolean.toString(b) + "\n");
        doWrite(cb);
    }

    public void println(final char c) {
        final CharBuffer cb = CharBuffer.wrap(Character.toString(c) + "\n");
        doWrite(cb);
    }

    public void println(final int i) {
        final CharBuffer cb = CharBuffer.wrap(Integer.toString(i) + "\n");
        doWrite(cb);
    }

    public void println(final long l) {
        final CharBuffer cb = CharBuffer.wrap(Long.toString(l) + "\n");
        doWrite(cb);
    }

    public void println(final float f) {
        final CharBuffer cb = CharBuffer.wrap(Float.toString(f) + "\n");
        doWrite(cb);
    }

    public void println(final double d) {
        final CharBuffer cb = CharBuffer.wrap(Double.toString(d) + "\n");
        doWrite(cb);
    }

    public void println(final char[] s) {
        final CharBuffer cb = CharBuffer.wrap(s + "\n");
        doWrite(cb);
    }

    public void println(final String s) {
        final CharBuffer cb = CharBuffer.wrap(s + "\n");
        doWrite(cb);
    }

    public void println(final Object obj) {
        final CharBuffer cb = CharBuffer.wrap(obj == null ? "null\n" : (obj.toString() + "\n"));
        doWrite(cb);
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
