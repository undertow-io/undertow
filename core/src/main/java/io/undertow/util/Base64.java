/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
public class Base64 {
    static byte[] ENCODING_TABLE;
    static byte[] DECODING_TABLE = new byte[80];

    static {
        try {
            ENCODING_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes("ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException();
        }

        for (int i = 0; i < ENCODING_TABLE.length; i++) {
            int v = (ENCODING_TABLE[i] & 0xFF) - 43;
            DECODING_TABLE[v] = (byte)i;
        }
    }

    public static Encoder encoder() {
        return new Encoder();
    }

    public static Decoder decoder() {
        return new Decoder();
    }

    public static class Encoder {
        private int state;
        private int last;

        public Encoder() {
        }

        public void encode(ByteBuffer source, ByteBuffer target) {
            if (target == null)
                throw new IllegalStateException();

            //  ( 6 | 2) (4 | 4) (2 | 6)
            int last = this.last;
            int state = this.state;
            while (source.remaining() > 0) {
                int b = source.get() & 0xFF;
                switch (state++) {
                    case 0:
                        target.put(ENCODING_TABLE[b >>> 2]);
                        last = (b & 0x3) << 4;
                        break;
                    case 1:
                        target.put(ENCODING_TABLE[last | (b >>> 4)]);
                        last = (b & 0x0F) << 2;
                        break;
                    case 2:
                        target.put(ENCODING_TABLE[last | (b >>> 6)]);
                        target.put(ENCODING_TABLE[b & 0x3F]);
                        state = last = 0;
                        break;
                }
            }

            this.last = last;
            this.state = state;
        }

        public void complete(ByteBuffer target) {
            if (state > 0) {
                target.put(ENCODING_TABLE[last]);
                for (int i = 0; i < state; i++)
                    target.put((byte)'=');

                last = state = 0;
            }
        }
    }

    public static class Decoder {
        private int state;
        private int last;
        private static int MARK = 0xFF00;

        public Decoder() {
        }

        public void decode(ByteBuffer source, ByteBuffer target) throws IOException {
            if (target == null)
                throw new IllegalStateException();

            int last = this.last;
            int state = this.state;

            while (source.remaining() > 0 && target.remaining() > 0) {
                int b = source.get() & 0xFF;
                if (last == MARK) {
                    if (b != '=') {
                        throw new IOException("Expected padding character");
                    }
                    last = state = 0;
                    break;
                }
                if (b == '=') {
                    switch (state) {
                        case 2:
                            last = MARK; state++;
                            break;
                        case 3:
                            this.last = this.state = 0;  // DONE!
                            return;
                        default: throw new IOException("Unexpected padding character");
                    }
                    continue;
                }
                if (b == ' ' || b == '\t' || b == '\r' || b == '\n') {
                    continue;
                }
                if (b < 43 || b > 122) {
                    throw new IOException("Invalid base64 character encountered: " + b);
                }
                b = DECODING_TABLE[b - 43] & 0xFF;

                //  ( 6 | 2) (4 | 4) (2 | 6)
                switch (state++) {
                    case 0:
                        last = b << 2;
                        break;
                    case 1:
                        target.put((byte)(last | (b >>> 4)));
                        last = (b & 0x0F) << 4;
                        break;
                    case 2:
                        target.put((byte)(last | (b >>> 2)));
                        last = (b & 0x3) << 6;
                        break;
                    case 3:
                        target.put((byte)(last | b));
                        last = state = 0;
                        break;
                }
            }

            this.last = last;
            this.state = state;
        }

        public void complete(ByteBuffer target) {
            if (state > 0) {
                target.put(ENCODING_TABLE[last]);
                for (int i = 0; i < state; i++)
                    target.put((byte)'=');
            }
        }
    }
}