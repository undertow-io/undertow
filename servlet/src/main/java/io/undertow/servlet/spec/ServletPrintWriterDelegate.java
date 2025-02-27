package io.undertow.servlet.spec;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;

import sun.reflect.ReflectionFactory;

/**
 * @author Stuart Douglas
 */
public final class ServletPrintWriterDelegate extends PrintWriter {
    private ServletPrintWriterDelegate() {
        super((OutputStream) null);
    }

    private static final Constructor<ServletPrintWriterDelegate> CONSTRUCTOR;

    static {
        CONSTRUCTOR = AccessController.doPrivileged(new PrivilegedAction<Constructor<ServletPrintWriterDelegate>>() {
            @Override
            public Constructor<ServletPrintWriterDelegate> run() {
                try {
                    return (Constructor<ServletPrintWriterDelegate>) ReflectionFactory.getReflectionFactory().newConstructorForSerialization(ServletPrintWriterDelegate.class, Object.class.getDeclaredConstructor());
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public static ServletPrintWriterDelegate newInstance(final ServletPrintWriter servletPrintWriter) {
        final ServletPrintWriterDelegate delegate;
        if (System.getSecurityManager() == null) {
            try {
                delegate = CONSTRUCTOR.newInstance();
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            delegate = AccessController.doPrivileged(new PrivilegedAction<ServletPrintWriterDelegate>() {
                @Override
                public ServletPrintWriterDelegate run() {
                    try {
                        return CONSTRUCTOR.newInstance();
                    } catch (InstantiationException e) {
                        throw new RuntimeException(e);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        delegate.setServletPrintWriter(servletPrintWriter);
        return delegate;
    }

    private ServletPrintWriter servletPrintWriter;

    public void setServletPrintWriter(final ServletPrintWriter servletPrintWriter) {
        this.servletPrintWriter = servletPrintWriter;
    }

    @Override
    public void flush() {
        servletPrintWriter.flush();
    }

    @Override
    public void close() {
        servletPrintWriter.close();
    }

    @Override
    public boolean checkError() {
        return servletPrintWriter.checkError();
    }

    @Override
    public void write(final int c) {
        servletPrintWriter.write(c);
    }

    @Override
    public void write(final char[] buf, final int off, final int len) {
        servletPrintWriter.write(buf, off, len);
    }

    @Override
    public void write(final char[] buf) {
        servletPrintWriter.write(buf);
    }

    @Override
    public void write(final String s, final int off, final int len) {
        servletPrintWriter.write(s, off, len);
    }

    @Override
    public void write(final String s) {
        servletPrintWriter.write(s);
    }

    @Override
    public void print(final boolean b) {
        servletPrintWriter.print(b);
    }

    @Override
    public void print(final char c) {
        servletPrintWriter.print(c);
    }

    @Override
    public void print(final int i) {
        servletPrintWriter.print(i);
    }

    @Override
    public void print(final long l) {
        servletPrintWriter.print(l);
    }

    @Override
    public void print(final float f) {
        servletPrintWriter.print(f);
    }

    @Override
    public void print(final double d) {
        servletPrintWriter.print(d);
    }

    @Override
    public void print(final char[] s) {
        servletPrintWriter.print(s);
    }

    @Override
    public void print(final String s) {
        servletPrintWriter.print(s);
    }

    @Override
    public void print(final Object obj) {
        servletPrintWriter.print(obj);
    }

    @Override
    public void println() {
        servletPrintWriter.println();
    }

    @Override
    public void println(final boolean x) {
        servletPrintWriter.println(x);
    }

    @Override
    public void println(final char x) {
        servletPrintWriter.println(x);
    }

    @Override
    public void println(final int x) {
        servletPrintWriter.println(x);
    }

    @Override
    public void println(final long x) {
        servletPrintWriter.println(x);
    }

    @Override
    public void println(final float x) {
        servletPrintWriter.println(x);
    }

    @Override
    public void println(final double x) {
        servletPrintWriter.println(x);
    }

    @Override
    public void println(final char[] x) {
        servletPrintWriter.println(x);
    }

    @Override
    public void println(final String x) {
        servletPrintWriter.println(x);
    }

    @Override
    public void println(final Object x) {
        servletPrintWriter.println(x);
    }

    @Override
    public PrintWriter printf(final String format, final Object... args) {
        servletPrintWriter.printf(format, args);
        return this;
    }

    @Override
    public PrintWriter printf(final Locale l, final String format, final Object... args) {
        servletPrintWriter.printf(l, format, args);
        return this;
    }

    @Override
    public PrintWriter format(final String format, final Object... args) {
        servletPrintWriter.format(format, args);
        return this;
    }

    @Override
    public PrintWriter format(final Locale l, final String format, final Object... args) {
        servletPrintWriter.format(l, format, args);
        return this;
    }

    @Override
    public PrintWriter append(final CharSequence csq) {
        servletPrintWriter.append(csq);
        return this;
    }

    @Override
    public PrintWriter append(final CharSequence csq, final int start, final int end) {
        servletPrintWriter.append(csq, start, end);
        return this;
    }

    @Override
    public PrintWriter append(final char c) {
        servletPrintWriter.append(c);
        return this;
    }

}
