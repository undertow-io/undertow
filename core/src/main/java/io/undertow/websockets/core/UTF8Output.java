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

package io.undertow.websockets.core;

import org.xnio.Buffers;

import java.nio.ByteBuffer;

/**
 * Utility class which allows to extract a UTF8 String from bytes respecting valid code-points
 */
public final class UTF8Output {
    private static final int UTF8_ACCEPT = 0;
    private static final byte HIGH_BIT = (byte) (1 << 7);

    private static final byte[] TYPES = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 7, 7, 7, 7,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 8,
            8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8,
            8, 8, 8, 8, 8, 8};

    private static final byte[] STATES = {0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12,
            12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12,
            12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
            12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 12, 12, 36,
            12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12,
            12, 12, 12, 12, 12, 12};

    @SuppressWarnings("RedundantFieldInitialization")
    private byte state = UTF8_ACCEPT;
    private int codep;

    private final StringBuilder stringBuilder;

    public UTF8Output(ByteBuffer... payload) {
        stringBuilder = new StringBuilder((int) Buffers.remaining(payload));
        write(payload);
    }

    public UTF8Output() {
        stringBuilder = new StringBuilder();
    }

    public void write(ByteBuffer... bytes) {
        for (ByteBuffer buf : bytes) {
            while (buf.hasRemaining()) {
                write(buf.get());
            }
        }
    }

    private void write(byte b) {
        if(state == UTF8_ACCEPT && (b & HIGH_BIT) == 0) {
            stringBuilder.append((char)b);
            return;
        }
        byte type = TYPES[b & 0xFF];

        codep = state != UTF8_ACCEPT ? b & 0x3f | codep << 6 : 0xff >> type & b;

        state = STATES[state + type];

        if (state == UTF8_ACCEPT) {
            for (char c : Character.toChars(codep)) {
                stringBuilder.append(c);
            }
        }
    }

    /**
     * Extract a String holding the utf8 text
     */
    public String extract() {
        String text = stringBuilder.toString();
        stringBuilder.setLength(0);
        return text;
    }

    public boolean hasData() {
        return stringBuilder.length() != 0;
    }
}
