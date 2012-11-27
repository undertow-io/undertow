package io.undertow.servlet.core;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

import io.undertow.servlet.spec.ServletOutputStreamImpl;

/**
 * Real servlet print writer functionality, that is not limited by extending
 * {@link java.io.PrintWriter}
 *
 * TODO: we really need to fix this, atm we need to flush every write so that we know when the response is complete
 * we can't just count the bytes because we don't know how they are going to be encoded
 *
 * @author Stuart Douglas
 */
public class ServletPrintWriter {

    private final PrintStream printStream;
    private final Integer contentLength;

    public ServletPrintWriter(final ServletOutputStreamImpl printStream, final String charset, final Integer contentLength) throws UnsupportedEncodingException {
        this.contentLength = contentLength;
        this.printStream = new PrintStream(printStream, false, charset);
    }

    public void flush() {
        printStream.flush();
    }

    public void close() {
        printStream.close();
    }

    public boolean checkError() {
        return printStream.checkError();
    }

    public void write(final int c) {
        printStream.write(c);
    }

    public void write(final char[] buf, final int off, final int len) {
        printStream.append(new String(buf), off, len);
    }

    public void write(final char[] buf) {
        printStream.append(new String(buf));
    }

    public void write(final String s, final int off, final int len) {
        printStream.append(s, off, len);
    }

    public void write(final String s) {
        printStream.append(s);
    }

    public void print(final boolean b) {
        printStream.print(b);
    }

    public void print(final char c) {
        printStream.print(c);
    }

    public void print(final int i) {
        printStream.print(i);
    }

    public void print(final long l) {
        printStream.print(l);
    }

    public void print(final float f) {
        printStream.print(f);
    }

    public void print(final double d) {
        printStream.print(d);
    }

    public void print(final char[] s) {
        printStream.print(s);
    }

    public void print(final String s) {
        printStream.print(s);
    }

    public void print(final Object obj) {
        printStream.print(obj);
    }

    public void println() {
        printStream.println();
    }

    public void println(final boolean x) {
        printStream.println(x);
    }

    public void println(final char x) {
        printStream.println(x);
    }

    public void println(final int x) {
        printStream.println(x);
    }

    public void println(final long x) {
        printStream.println(x);
    }

    public void println(final float x) {
        printStream.println(x);
    }

    public void println(final double x) {
        printStream.println(x);
    }

    public void println(final char[] x) {
        printStream.println(x);
    }

    public void println(final String x) {
        printStream.println(x);
    }

    public void println(final Object x) {
        printStream.println(x);
    }

    public void printf(final String format, final Object... args) {
        printStream.printf(format, args);
    }


    public void printf(final Locale l, final String format, final Object... args) {
        printStream.printf(l, format, args);
    }


    public void format(final String format, final Object... args) {
        printStream.format(format, args);
    }


    public void format(final Locale l, final String format, final Object... args) {
        printStream.format(l, format, args);
    }


    public void append(final CharSequence csq) {
        printStream.append(csq);
    }


    public void append(final CharSequence csq, final int start, final int end) {
        printStream.append(csq, start, end);
    }


    public void append(final char c) {
        printStream.append(c);
    }

}
