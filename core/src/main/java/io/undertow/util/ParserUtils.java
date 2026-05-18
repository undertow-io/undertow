/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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

import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:opalka.richard@gmail.com">Richard Opalka</a>
 */
public final class ParserUtils {

    // char constants
    public static final byte AMPERSAND = '&';
    public static final byte ASTERISK = '*';
    public static final byte COLON = ':';
    public static final byte COMMA = ',';
    public static final byte CARRIAGE_RETURN = '\r';
    public static final byte DOT = '.';
    public static final byte EQUALS = '=';
    public static final byte LEFT_SQUARE_BRACKET = '[';
    public static final byte LINE_FEED = '\n';
    public static final byte PERCENT = '%';
    public static final byte PLUS  = '+';
    public static final byte QUESTION = '?';
    public static final byte RIGHT_SQUARE_BRACKET = ']';
    public static final byte SEMICOLON = ';';
    public static final byte SLASH = '/';
    public static final byte SPACE = ' ';

    // masks and utils
    private static final int MAXIMUM_REQUEST_METHOD_LENGTH = 1 << 5;
    private static final int FIRST_64_MASK = 0b1100_0000;
    private static final int FIRST_128_MASK = 0b1000_0000;
    private static final long DECIMAL_DIGITS_LOWER = 0b00000011_11111111_00000000_00000000_00000000_00000000_00000000_00000000L;
    private static final long IPV4_LOWER = 0b00000011_11111111_01000000_00000000_00000000_00000000_00000000_00000000L;
    private static final long IPV6_LOWER = 0b00000111_11111111_00000000_00000000_00000000_00000000_00000000_00000000L;
    private static final long DNS_LOWER = 0b00101011_11111111_01111111_11010010_00000000_00000000_00000000_00000000L;
    private static final long DNS_UPPER = 0b01000111_11111111_11111111_11111110_10000111_11111111_11111111_11111110L;
    private static final long HEXADECIMAL_DIGITS_UPPER = 0b00000000_00000000_00000000_01111110_00000000_00000000_00000000_01111110L;
    private static final long PATH_SEGMENT_LOWER = 0b00101111_11111111_01111111_11110010_00000000_00000000_00000000_00000000L;
    private static final long PATH_SEGMENT_UPPER = 0b01000111_11111111_11111111_11111110_10000111_11111111_11111111_11111111L;
    private static final long REQUEST_TARGET_LOWER = 0b10101111_11111111_11111111_11110010_00000000_00000000_00000000_00000000L;
    private static final long REQUEST_TARGET_UPPER = 0b01000111_11111111_11111111_11111110_10101111_11111111_11111111_11111111L;
    private static final long SCHEME_LOWER = 0b00000011_11111111_01101000_00000000_00000000_00000000_00000000_00000000L;
    private static final long SCHEME_UPPER = 0b00000111_11111111_11111111_11111110_00000111_11111111_11111111_11111110L;
    private static final long SPACE_OR_TAB_LOWER = 0b00000000_00000000_00000000_00000001_00000000_00000000_00000010_00000000L;
    private static final long TOKEN_LOWER = 0b00000011_11111111_01101100_11111010_00000000_00000000_00000000_00000000L;
    private static final long TOKEN_UPPER = 0b01010111_11111111_11111111_11111111_11000111_11111111_11111111_11111110L;
    private static final long VISIBLE_ASCII_LOWER = 0b11111111_11111111_11111111_11111110_00000000_00000000_00000000_00000000L;
    private static final long VISIBLE_ASCII_UPPER = 0b01111111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L;
    private static final long ALPHA_ASCII_UPPER = 0b00000111_11111111_11111111_11111110_00000111_11111111_11111111_11111110L;
    private static final byte[] PROTOCOL = "HTTP/1.1".getBytes(StandardCharsets.US_ASCII);
    private static final int PROTOCOL_DIGIT_INDEX_1 = 5;
    private static final int PROTOCOL_DIGIT_INDEX_2 = 7;

    private ParserUtils() {
        // forbidden instantiation
    }

    public static int getMaximumRequestMethodLength() {
        return MAXIMUM_REQUEST_METHOD_LENGTH;
    }

    public static int getProtocolLength() {
        return PROTOCOL.length;
    }

    public static boolean isRequestTargetChar(byte c) {
        return applyBothMasks(c, REQUEST_TARGET_LOWER, REQUEST_TARGET_UPPER);
    }

    public static boolean isDNSNameChar(final byte c) {
        return applyBothMasks(c, DNS_LOWER, DNS_UPPER);
    }

    public static boolean isIPv4AddressChar(final byte c) {
        return applyLowerMask(c, IPV4_LOWER);
    }

    public static boolean isIPv6AddressChar(final byte c) {
        return applyBothMasks(c, IPV6_LOWER, HEXADECIMAL_DIGITS_UPPER);
    }

    public static boolean isAlphaChar(final byte c) {
        return applyUpperMask(c, ALPHA_ASCII_UPPER);
    }

    public static boolean isSchemeChar(final byte c) {
        return applyBothMasks(c, SCHEME_LOWER, SCHEME_UPPER);
    }

    public static boolean isTokenChar(final byte c) {
        return applyBothMasks(c, TOKEN_LOWER, TOKEN_UPPER);
    }

    public static boolean isObsoleteChar(final byte c) {
        return (c & FIRST_128_MASK) != 0;
    }

    public static boolean isVisibleAsciiChar(final byte c) {
        return applyBothMasks(c, VISIBLE_ASCII_LOWER, VISIBLE_ASCII_UPPER);
    }

    public static boolean isSpaceOrTabChar(final byte c) {
        return applyLowerMask(c, SPACE_OR_TAB_LOWER);
    }

    public static boolean isProtocolChar(final byte c, final int index) {
        boolean isDigitIndex = index == PROTOCOL_DIGIT_INDEX_1 || index == PROTOCOL_DIGIT_INDEX_2;
        return 0 <= index && index < PROTOCOL.length && (c == PROTOCOL[index] || (isDigitIndex && isDigitChar(c)));
    }

    public static boolean isPathSegmentChar(final byte c) {
        return applyBothMasks(c, PATH_SEGMENT_LOWER, PATH_SEGMENT_UPPER);
    }

    public static boolean isDigitChar(final byte c) {
        return applyLowerMask(c, DECIMAL_DIGITS_LOWER);
    }

    public static boolean isHexDigitChar(final byte c) {
        return applyBothMasks(c, DECIMAL_DIGITS_LOWER, HEXADECIMAL_DIGITS_UPPER);
    }

    private static boolean applyLowerMask(final byte c, final long lowerMask) {
        return (c & FIRST_64_MASK) == 0 && (lowerMask & (1L << c)) != 0;
    }

    private static boolean applyUpperMask(final byte c, final long upperMask) {
        return (c & FIRST_128_MASK) == 0 && ((c & FIRST_64_MASK) != 0) && (upperMask & (1L << (c & ~FIRST_64_MASK))) != 0;
    }

    private static boolean applyBothMasks(final byte c, final long lowerMask, final long upperMask) {
        return ((c & FIRST_128_MASK) == 0 ) && ((c & FIRST_64_MASK) == 0 ? (lowerMask & (1L << (c))) : (upperMask & (1L << (c & ~FIRST_64_MASK)))) != 0;
    }
}
