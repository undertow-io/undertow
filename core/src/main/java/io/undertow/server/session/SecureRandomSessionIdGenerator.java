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

package io.undertow.server.session;

import java.security.SecureRandom;

/**
 * A {@link SessionIdGenerator} that uses a secure random to generate a
 * session ID.
 *
 * On some systems this may perform poorly if not enough entropy is available,
 * depending on the algorithm in use.
 *
 *
 * @author Stuart Douglas
 */
public class SecureRandomSessionIdGenerator implements SessionIdGenerator {

    private final SecureRandom random = new SecureRandom();

    private volatile int length = 30;

    private static final char[] SESSION_ID_ALPHABET;

    private static final String ALPHABET_PROPERTY = "io.undertow.server.session.SecureRandomSessionIdGenerator.ALPHABET";

    static {
        String alphabet = System.getProperty(ALPHABET_PROPERTY, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_");
        if(alphabet.length() != 64) {
            throw new RuntimeException("io.undertow.server.session.SecureRandomSessionIdGenerator must be exactly 64 characters long");
        }
        SESSION_ID_ALPHABET = alphabet.toCharArray();
    }

    @Override
    public String createSessionId() {
        final byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return new String(encode(bytes));
    }


    public int getLength() {
        return length;
    }

    public void setLength(final int length) {
        this.length = length;
    }

    /**
     * Encode the bytes into a String with a slightly modified Base64-algorithm
     * This code was written by Kevin Kelley <kelley@ruralnet.net>
     * and adapted by Thomas Peuss <jboss@peuss.de>
     *
     * @param data The bytes you want to encode
     * @return the encoded String
     */
    private char[] encode(byte[] data) {
        char[] out = new char[((data.length + 2) / 3) * 4];
        char[] alphabet = SESSION_ID_ALPHABET;
        //
        // 3 bytes encode to 4 chars.  Output is always an even
        // multiple of 4 characters.
        //
        for (int i = 0, index = 0; i < data.length; i += 3, index += 4) {
            boolean quad = false;
            boolean trip = false;

            int val = (0xFF & (int) data[i]);
            val <<= 8;
            if ((i + 1) < data.length) {
                val |= (0xFF & (int) data[i + 1]);
                trip = true;
            }
            val <<= 8;
            if ((i + 2) < data.length) {
                val |= (0xFF & (int) data[i + 2]);
                quad = true;
            }
            out[index + 3] = alphabet[(quad ? (val & 0x3F) : 63)];
            val >>= 6;
            out[index + 2] = alphabet[(trip ? (val & 0x3F) : 63)];
            val >>= 6;
            out[index + 1] = alphabet[val & 0x3F];
            val >>= 6;
            out[index] = alphabet[val & 0x3F];
        }
        return out;
    }
}
