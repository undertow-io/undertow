/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;

/**
 * General bit-affecting utility methods.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Bits {
    private Bits() {}

    //--- Bit mask methods

    /**
     * Get an integer bit mask consisting of 1 bits in the given range.  The returned {@code int}
     * will have bits {@code low} through {@code high} set, and all other bits clear.
     *
     * @param low the low bit value
     * @param high the high bit value
     * @return the bit mask
     */
    public static int intBitMask(int low, int high) {
        assert low >= 0;
        assert low <= high;
        assert high < 32;
        return (high == 31 ? 0 : (1 << high + 1)) - (1 << low);
    }

    /**
     * Get a long bit mask consisting of 1 bits in the given range.  The returned {@code long}
     * will have bits {@code low} through {@code high} set, and all other bits clear.
     *
     * @param low the low bit value
     * @param high the high bit value
     * @return the bit mask
     */
    public static long longBitMask(int low, int high) {
        assert low >= 0;
        assert low <= high;
        assert high < 64;
        return (high == 63 ? 0L : (1L << (long) high + 1L)) - (1L << (long) low);
    }

    //--- Flags methods

    /**
     * Determine if any of the {@code flags} in the given {@code var} are set.
     *
     * @param var the value to test
     * @param flags the flags to test for
     * @return {@code true} if any of {@code flags} are in {@code var}, {@code false} otherwise
     */
    public static boolean anyAreSet(int var, int flags) {
        return (var & flags) != 0;
    }

    /**
     * Determine if all of the {@code flags} in the given {@code var} are set.
     *
     * @param var the value to test
     * @param flags the flags to test for
     * @return {@code true} if all of {@code flags} are in {@code var}, {@code false} otherwise
     */
    public static boolean allAreSet(int var, int flags) {
        return (var & flags) == flags;
    }

    /**
     * Determine if any of the {@code flags} in the given {@code var} are clear.
     *
     * @param var the value to test
     * @param flags the flags to test for
     * @return {@code true} if not all of {@code flags} are in {@code var}, {@code false} otherwise
     */
    public static boolean anyAreClear(int var, int flags) {
        return (var & flags) != flags;
    }

    /**
     * Determine if all of the {@code flags} in the given {@code var} are clear.
     *
     * @param var the value to test
     * @param flags the flags to test for
     * @return {@code true} if none of {@code flags} are in {@code var}, {@code false} otherwise
     */
    public static boolean allAreClear(int var, int flags) {
        return (var & flags) == 0;
    }

    /**
     * Determine if any of the {@code flags} in the given {@code var} are set.
     *
     * @param var the value to test
     * @param flags the flags to test for
     * @return {@code true} if any of {@code flags} are in {@code var}, {@code false} otherwise
     */
    public static boolean anyAreSet(long var, long flags) {
        return (var & flags) != 0;
    }

    /**
     * Determine if all of the {@code flags} in the given {@code var} are set.
     *
     * @param var the value to test
     * @param flags the flags to test for
     * @return {@code true} if all of {@code flags} are in {@code var}, {@code false} otherwise
     */
    public static boolean allAreSet(long var, long flags) {
        return (var & flags) == flags;
    }

    /**
     * Determine if any of the {@code flags} in the given {@code var} are clear.
     *
     * @param var the value to test
     * @param flags the flags to test for
     * @return {@code true} if not all of {@code flags} are in {@code var}, {@code false} otherwise
     */
    public static boolean anyAreClear(long var, long flags) {
        return (var & flags) != flags;
    }

    /**
     * Determine if all of the {@code flags} in the given {@code var} are clear.
     *
     * @param var the value to test
     * @param flags the flags to test for
     * @return {@code true} if none of {@code flags} are in {@code var}, {@code false} otherwise
     */
    public static boolean allAreClear(long var, long flags) {
        return (var & flags) == 0;
    }

    //--- Signed/unsigned methods

    /**
     * Convert a signed value to unsigned.
     *
     * @param v the signed byte
     * @return the unsigned byte, as an int
     */
    public static int unsigned(byte v) {
        return v & 0xff;
    }

    /**
     * Convert a signed value to unsigned.
     *
     * @param v the signed short
     * @return the unsigned short, as an int
     */
    public static int unsigned(short v) {
        return v & 0xffff;
    }

    /**
     * Convert a signed value to unsigned.
     *
     * @param v the signed int
     * @return the unsigned int, as a long
     */
    public static long unsigned(int v) {
        return v & 0xffffffffL;
    }

    //--- Byte array read methods

    /**
     * Get a 16-bit signed little-endian short value from a byte array.
     *
     * @param b the byte array
     * @param off the offset in the array
     * @return the signed short value
     */
    public static short shortFromBytesLE(byte[] b, int off) {
        return (short) (b[off + 1] << 8 | b[off] & 0xff);
    }

    /**
     * Get a 16-bit signed big-endian short value from a byte array.
     *
     * @param b the byte array
     * @param off the offset in the array
     * @return the signed short value
     */
    public static short shortFromBytesBE(byte[] b, int off) {
        return (short) (b[off] << 8 | b[off + 1] & 0xff);
    }

    /**
     * Get a 16-bit signed little-endian char value from a byte array.
     *
     * @param b the byte array
     * @param off the offset in the array
     * @return the signed char value
     */
    public static char charFromBytesLE(byte[] b, int off) {
        return (char) (b[off + 1] << 8 | b[off] & 0xff);
    }

    /**
     * Get a 16-bit signed big-endian char value from a byte array.
     *
     * @param b the byte array
     * @param off the offset in the array
     * @return the signed char value
     */
    public static char charFromBytesBE(byte[] b, int off) {
        return (char) (b[off] << 8 | b[off + 1] & 0xff);
    }

    /**
     * Get a 24-bit signed little-endian int value from a byte array.
     *
     * @param b the byte array
     * @param off the offset in the array
     * @return the signed medium value as an int
     */
    public static int mediumFromBytesLE(byte[] b, int off) {
        return (b[off + 2] & 0xff) << 16 | (b[off + 1] & 0xff) << 8 | b[off] & 0xff;
    }

    /**
     * Get a 24-bit signed big-endian int value from a byte array.
     *
     * @param b the byte array
     * @param off the offset in the array
     * @return the signed medium value as an int
     */
    public static int mediumFromBytesBE(byte[] b, int off) {
        return (b[off] & 0xff) << 16 | (b[off + 1] & 0xff) << 8 | b[off + 2] & 0xff;
    }

    /**
     * Get a 32-bit signed little-endian int value from a byte array.
     *
     * @param b the byte array
     * @param off the offset in the array
     * @return the signed int value
     */
    public static int intFromBytesLE(byte[] b, int off) {
        return b[off + 3] << 24 | (b[off + 2] & 0xff) << 16 | (b[off + 1] & 0xff) << 8 | b[off] & 0xff;
    }

    /**
     * Get a 32-bit signed big-endian int value from a byte array.
     *
     * @param b the byte array
     * @param off the offset in the array
     * @return the signed int value
     */
    public static int intFromBytesBE(byte[] b, int off) {
        return b[off] << 24 | (b[off + 1] & 0xff) << 16 | (b[off + 2] & 0xff) << 8 | b[off + 3] & 0xff;
    }

    /**
     * Get a 64-bit signed little-endian long value from a byte array.
     *
     * @param b the byte array
     * @param off the offset in the array
     * @return the signed long value
     */
    public static long longFromBytesLE(byte[] b, int off) {
        return (b[off + 7] & 0xffL) << 56L | (b[off + 6] & 0xffL) << 48L | (b[off + 5] & 0xffL) << 40L | (b[off + 4] & 0xffL) << 32L | (b[off + 3] & 0xffL) << 24L | (b[off + 2] & 0xffL) << 16L | (b[off + 1] & 0xffL) << 8L | b[off] & 0xffL;
    }

    /**
     * Get a 64-bit signed big-endian long value from a byte array.
     *
     * @param b the byte array
     * @param off the offset in the array
     * @return the signed long value
     */
    public static long longFromBytesBE(byte[] b, int off) {
        return (b[off] & 0xffL) << 56L | (b[off + 1] & 0xffL) << 48L | (b[off + 2] & 0xffL) << 40L | (b[off + 3] & 0xffL) << 32L | (b[off + 4] & 0xffL) << 24L | (b[off + 5] & 0xffL) << 16L | (b[off + 6] & 0xffL) << 8L | b[off + 7] & 0xffL;
    }

}
