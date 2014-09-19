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

package io.undertow.protocols.http2;

/**
 * @author Stuart Douglas
 */
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

import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Encoder for HPACK frames.
 *
 * @author Stuart Douglas
 */
public class HpackEncoder extends Hpack {

    /**
     * current bit pos for Huffman
     */
    private int currentBitPos;

    private long headersIterator = -1;
    private boolean firstPass = true;

    private HeaderMap currentHeaders;

    private static final Map<HttpString, StaticTableEntry[]> ENCODING_STATIC_TABLE;

    static {
        Map<HttpString, StaticTableEntry[]> map = new HashMap<>();
        for (int i = 1; i < STATIC_TABLE.length; ++i) {
            HeaderField m = STATIC_TABLE[i];
            StaticTableEntry[] existing = map.get(m.name);
            if (existing == null) {
                map.put(m.name, new StaticTableEntry[]{new StaticTableEntry(m.value, i)});
            } else {
                StaticTableEntry[] newEntry = new StaticTableEntry[existing.length + 1];
                System.arraycopy(existing, 0, newEntry, 0, existing.length);
                newEntry[existing.length] = new StaticTableEntry(m.value, i);
                map.put(m.name, newEntry);
            }
        }
        ENCODING_STATIC_TABLE = Collections.unmodifiableMap(map);
    }

    /**
     * the table size, not used at the moment cause this implementation does not use indexing yet
     */
    private int tableSize;

    /**
     * If a buffer does not have space to put some bytes we decrease its position by one, and store the bits here.
     * When a new
     */
    private int extraData;


    public HpackEncoder(int tableSize) {
        this.tableSize = tableSize;
    }

    /**
     * Encodes the headers into a buffer.
     * <p/>
     * Note that as it looks like the reference set will be dropped the first instruction that is encoded
     * in every case in an instruction to clear the reference set.
     * <p/>
     * TODO: this is super crappy at the moment, needs to be fixed up, in particular it does no actual compression at the moment
     *
     * @param headers
     * @param target
     */
    public State encode(HeaderMap headers, ByteBuffer target) {
        if (target.remaining() < 3) {
            return State.UNDERFLOW;
        }
        long it = headersIterator;
        if (headersIterator == -1) {
            //new headers map
            currentBitPos = 0;
            it = headers.fastIterate();
            currentHeaders = headers;
            //first push a reference set clear context update
            //as the reference set is going away this allows us to be compliant with HPACK 08 without doing a heap of extra useless work
        } else {
            if (headers != currentHeaders) {
                throw new IllegalStateException();
            }
            if (currentBitPos > 0) {
                //put the extra bits into the new buffer
                target.put((byte) extraData);
            }
        }
        while (it != -1) {
            HeaderValues values = headers.fiCurrent(it);
            boolean skip = false;
            if (firstPass) {
                if (values.getHeaderName().byteAt(0) != ':') {
                    skip = true;
                }
            } else {
                if (values.getHeaderName().byteAt(0) == ':') {
                    skip = true;
                }
            }
            if (!skip) {
                //initial super crappy implementation: just write everything out as literal header field never indexed
                //makes things much simpler
                for (int i = 0; i < values.size(); ++i) {

                    int required = 11 + values.getHeaderName().length(); //we use 11 to make sure we have enough room for the variable length itegers

                    StaticTableEntry[] staticTable = ENCODING_STATIC_TABLE.get(values.getHeaderName());

                    String val = values.get(i);
                    required += (1 + val.length());

                    if (target.remaining() < required) {
                        this.headersIterator = it;
                        this.currentBitPos = 0; //we don't use huffman yet
                        return State.UNDERFLOW;
                    }
                    if (staticTable == null) {
                        target.put((byte) 0);
                        target.put((byte) 0); //to use encodeInteger we need to place the first byte in the buffer.
                        encodeInteger(target, values.getHeaderName().length(), 7);
                        values.getHeaderName().appendTo(target);
                    } else {
                        boolean found = false;
                        for(StaticTableEntry st : staticTable) {
                            if(st.value != null && st.value.equals(val)) { //todo: some form of lookup?
                                target.put((byte) (1 << 7));
                                encodeInteger(target, st.pos, 7);
                                found = true;
                                break;
                            }
                        }
                        if(found) {
                            continue; //value was in static table, no need to encode
                        }
                        target.put((byte) 0);
                        encodeInteger(target, staticTable[0].pos, 4);
                    }
                    target.put((byte) 0); //to use encodeInteger we need to place the first byte in the buffer.
                    encodeInteger(target, val.length(), 7);
                    for (int j = 0; j < val.length(); ++j) {
                        target.put((byte) val.charAt(j));
                    }

                }
            }
            it = headers.fiNext(it);
            if (it == -1 && firstPass) {
                firstPass = false;
                it = headers.fastIterate();
            }
        }
        headersIterator = -1;
        firstPass = true;
        return State.COMPLETE;
    }

    /**
     * Push the n least significant bits of value into the buffer
     *
     * @param buffer        The Buffer to push into
     * @param value         The bits to push into the buffer
     * @param n             The number of bits to push
     * @param currentBitPos Value between 0 and 7 specifying the current location of the pit pointer
     */
    static int pushBits(ByteBuffer buffer, int value, int n, int currentBitPos) {

        int bitsLeft = n;
        if (currentBitPos != 0) {
            int rem = 8 - currentBitPos;
            //deal with the first partial byte, after that it is full bytes
            int forThisByte = n > rem ? rem : n;
            //now we left shift the value to leave only the bits we want
            int toPush = value >> (n - forThisByte);
            //how far we need to shift right
            int shift = 8 - (currentBitPos + forThisByte);
            int pos = buffer.position() - 1;
            buffer.put(pos, (byte) (buffer.get(pos) | (toPush << shift)));
            bitsLeft -= forThisByte;
            if (bitsLeft == 0) {
                int newPos = currentBitPos + n;
                return newPos == 8 ? 0 : newPos;
            }
            //ok, we have dealt with the first partial byte in the buffer
        }
        while (true) {
            int forThisByte = bitsLeft > 8 ? 8 : bitsLeft;
            int toPush = value >> (bitsLeft - forThisByte);
            int shift = 8 - forThisByte;
            buffer.put((byte) (toPush << shift));
            bitsLeft -= forThisByte;
            if (bitsLeft == 0) {
                return forThisByte;
            }
        }
    }

    public enum State {
        COMPLETE,
        UNDERFLOW,

    }

    static final class StaticTableEntry {
        final String value;
        final int pos;

        private StaticTableEntry(final String value, final int pos) {
            this.value = value;
            this.pos = pos;
        }
    }
}
