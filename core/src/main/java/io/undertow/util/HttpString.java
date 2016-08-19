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
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Random;

import static java.lang.Integer.signum;
import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOfRange;

import io.undertow.UndertowMessages;

/**
 * An HTTP case-insensitive Latin-1 string.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class HttpString implements Comparable<HttpString>, Serializable {
    private static final long serialVersionUID = 1L;

    private final byte[] bytes;
    private final transient int hashCode;
    /**
     * And integer that is only set for well known header to make
     * comparison fast
     */
    private final int orderInt;
    private transient String string;

    private static final Field hashCodeField;
    private static final int hashCodeBase;

    static {
        try {
            hashCodeField = HttpString.class.getDeclaredField("hashCode");
            hashCodeField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new NoSuchFieldError(e.getMessage());
        }
        hashCodeBase = new Random().nextInt();
    }

    /**
     * Empty HttpString instance.
     */
    public static final HttpString EMPTY = new HttpString("");

    /**
     * Construct a new instance.
     *
     * @param bytes the byte array to copy
     */
    public HttpString(final byte[] bytes) {
        this(bytes.clone(), null);
    }

    /**
     * Construct a new instance.
     *
     * @param bytes  the byte array to copy
     * @param offset the offset into the array to start copying
     * @param length the number of bytes to copy
     */
    public HttpString(final byte[] bytes, int offset, int length) {
        this(copyOfRange(bytes, offset, offset + length), null);
    }

    /**
     * Construct a new instance by reading the remaining bytes from a buffer.
     *
     * @param buffer the buffer to read
     */
    public HttpString(final ByteBuffer buffer) {
        this(take(buffer), null);
    }

    /**
     * Construct a new instance from a {@code String}.  The {@code String} will be used
     * as the cached {@code toString()} value for this {@code HttpString}.
     *
     * @param string the source string
     */
    public HttpString(final String string) {
        this(string, 0);
    }

    HttpString(final String string, int orderInt) {
        this.orderInt = orderInt;
        final int len = string.length();
        final byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            char c = string.charAt(i);
            if (c > 0xff) {
                throw new IllegalArgumentException("Invalid string contents " + string);
            }
            bytes[i] = (byte) c;
        }
        this.bytes = bytes;
        this.hashCode = calcHashCode(bytes);
        this.string = string;
        checkForNewlines();
    }

    private void checkForNewlines() {
        for(byte b : bytes) {
            if(b == '\r' || b == '\n') {
                throw UndertowMessages.MESSAGES.newlineNotSupportedInHttpString(string);
            }
        }
    }

    private HttpString(final byte[] bytes, final String string) {
        this.bytes = bytes;
        this.hashCode = calcHashCode(bytes);
        this.string = string;
        this.orderInt = 0;
        checkForNewlines();
    }

    /**
     * Attempt to convert a {@code String} to an {@code HttpString}.  If the string cannot be converted,
     * {@code null} is returned.
     *
     * @param string the string to try
     * @return the HTTP string, or {@code null} if the string is not in a compatible encoding
     */
    public static HttpString tryFromString(String string) {
        HttpString cached = Headers.fromCache(string);
        if(cached != null) {
            return cached;
        }
        final int len = string.length();
        final byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            char c = string.charAt(i);
            if (c > 0xff) {
                return null;
            }
            bytes[i] = (byte) c;
        }
        return new HttpString(bytes, string);
    }

    /**
     * Get the string length.
     *
     * @return the string length
     */
    public int length() {
        return bytes.length;
    }

    /**
     * Get the byte at an index.
     *
     * @return the byte at an index
     */
    public byte byteAt(int idx) {
        return bytes[idx];
    }

    /**
     * Copy {@code len} bytes from this string at offset {@code srcOffs} to the given array at the given offset.
     *
     * @param srcOffs the source offset
     * @param dst     the destination
     * @param offs    the destination offset
     * @param len     the number of bytes to copy
     */
    public void copyTo(int srcOffs, byte[] dst, int offs, int len) {
        arraycopy(bytes, srcOffs, dst, offs, len);
    }

    /**
     * Copy {@code len} bytes from this string to the given array at the given offset.
     *
     * @param dst  the destination
     * @param offs the destination offset
     * @param len  the number of bytes
     */
    public void copyTo(byte[] dst, int offs, int len) {
        copyTo(0, dst, offs, len);
    }

    /**
     * Copy all the bytes from this string to the given array at the given offset.
     *
     * @param dst  the destination
     * @param offs the destination offset
     */
    public void copyTo(byte[] dst, int offs) {
        copyTo(dst, offs, bytes.length);
    }

    /**
     * Append to a byte buffer.
     *
     * @param buffer the buffer to append to
     */
    public void appendTo(ByteBuffer buffer) {
        buffer.put(bytes);
    }

    /**
     * Append to an output stream.
     *
     * @param output the stream to write to
     * @throws IOException if an error occurs
     */
    public void writeTo(OutputStream output) throws IOException {
        output.write(bytes);
    }

    private static byte[] take(final ByteBuffer buffer) {
        if (buffer.hasArray()) {
            // avoid useless array clearing
            try {
                return copyOfRange(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            } finally {
                buffer.position(buffer.limit());
            }
        } else {
            final byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
    }

    /**
     * Compare this string to another in a case-insensitive manner.
     *
     * @param other the other string
     * @return -1, 0, or 1
     */
    public int compareTo(final HttpString other) {
        if(orderInt != 0 && other.orderInt != 0) {
            return signum(orderInt - other.orderInt);
        }
        final int len = Math.min(bytes.length, other.bytes.length);
        int res;
        for (int i = 0; i < len; i++) {
            res = signum(higher(bytes[i]) - higher(other.bytes[i]));
            if (res != 0) return res;
        }
        // shorter strings sort higher
        return signum(bytes.length - other.bytes.length);
    }

    /**
     * Get the hash code.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * Determine if this {@code HttpString} is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(final Object other) {
        if(other == this) {
            return true;
        }
        if(!(other instanceof HttpString)) {
            return false;
        }
        HttpString otherString = (HttpString) other;
        if(orderInt > 0 && otherString.orderInt > 0) {
            //if the order int is set for both of them and different then we know they are different strings
            return false;
        }
        return bytesAreEqual(bytes, otherString.bytes);
    }

    /**
     * Determine if this {@code HttpString} is equal to another.
     *
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(final HttpString other) {
        return other == this || other != null && bytesAreEqual(bytes, other.bytes);
    }

    private static int calcHashCode(final byte[] bytes) {
        int hc = 17;
        for (byte b : bytes) {
            hc = (hc << 4) + hc + higher(b);
        }
        return hc;
    }

    private static int higher(byte b) {
        return b & (b >= 'a' && b <= 'z' ? 0xDF : 0xFF);
    }

    private static boolean bytesAreEqual(final byte[] a, final byte[] b) {
        return a.length == b.length && bytesAreEquivalent(a, b);
    }

    private static boolean bytesAreEquivalent(final byte[] a, final byte[] b) {
        assert a.length == b.length;
        final int len = a.length;
        for (int i = 0; i < len; i++) {
            if (higher(a[i]) != higher(b[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the {@code String} representation of this {@code HttpString}.
     *
     * @return the string
     */
    @Override
    @SuppressWarnings("deprecation")
    public String toString() {
        if (string == null) {
            string = new String(bytes, 0);
        }
        return string;
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        ois.defaultReadObject();
        try {
            hashCodeField.setInt(this, calcHashCode(bytes));
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    static int hashCodeOf(String headerName) {
        int hc = 17;

        for (int i = 0; i < headerName.length(); ++i) {
            hc = (hc << 4) + hc + higher((byte) headerName.charAt(i));
        }
        return hc;
    }

    public boolean equalToString(String headerName) {
        if(headerName.length() != bytes.length) {
            return false;
        }

        final int len = bytes.length;
        for (int i = 0; i < len; i++) {
            if (higher(bytes[i]) != higher((byte)headerName.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
