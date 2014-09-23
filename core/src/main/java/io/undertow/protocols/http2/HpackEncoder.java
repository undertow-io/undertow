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

import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encoder for HPACK frames.
 *
 * @author Stuart Douglas
 */
public class HpackEncoder extends Hpack {

    public static final IndexFunction DEFAULT_INDEX_FUNCTION = new IndexFunction() {
        @Override
        public boolean shouldUseIndexing(HttpString headerName, String value) {
            return !headerName.equals(Headers.CONTENT_LENGTH);
        }
    };

    /**
     * current bit pos for Huffman
     */
    private int currentBitPos;

    private long headersIterator = -1;
    private boolean firstPass = true;

    private HeaderMap currentHeaders;

    private int entryPositionCounter;

    private static final Map<HttpString, TableEntry[]> ENCODING_STATIC_TABLE;

    private final Deque<TableEntry> evictionQueue = new ArrayDeque<>();
    private final Map<HttpString, List<TableEntry>> dynamicTable = new HashMap<>(); //TODO: use a custom data structure to reduce allocations

    static {
        Map<HttpString, TableEntry[]> map = new HashMap<>();
        for (int i = 1; i < STATIC_TABLE.length; ++i) {
            HeaderField m = STATIC_TABLE[i];
            TableEntry[] existing = map.get(m.name);
            if (existing == null) {
                map.put(m.name, new TableEntry[]{new TableEntry(m.name, m.value, i)});
            } else {
                TableEntry[] newEntry = new TableEntry[existing.length + 1];
                System.arraycopy(existing, 0, newEntry, 0, existing.length);
                newEntry[existing.length] = new TableEntry(m.name, m.value, i);
                map.put(m.name, newEntry);
            }
        }
        ENCODING_STATIC_TABLE = Collections.unmodifiableMap(map);
    }

    /**
     * The maximum table size
     */
    private int maxTableSize;

    /**
     * The current table size
     */
    private int currentTableSize;

    /**
     * If a buffer does not have space to put some bytes we decrease its position by one, and store the bits here.
     * When a new
     */
    private int extraData;

    private final IndexFunction indexFunction = DEFAULT_INDEX_FUNCTION;

    public HpackEncoder(int maxTableSize) {
        this.maxTableSize = maxTableSize;
    }

    /**
     * Encodes the headers into a buffer.
     * <p/>
     * Note that as it looks like the reference set will be dropped the first instruction that is encoded
     * in every case in an instruction to clear the reference set.
     * <p/>
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

                    HttpString headerName = values.getHeaderName();
                    int required = 11 + headerName.length(); //we use 11 to make sure we have enough room for the variable length itegers

                    String val = values.get(i);
                    TableEntry tableEntry = findInTable(headerName, val);

                    required += (1 + val.length());

                    if (target.remaining() < required) {
                        this.headersIterator = it;
                        this.currentBitPos = 0; //we don't use huffman yet
                        return State.UNDERFLOW;
                    }
                    boolean canIndex = indexFunction.shouldUseIndexing(headerName, val);
                    if (tableEntry == null && canIndex) {
                        //add the entry to the dynamic table
                        target.put((byte) (1 << 6));
                        target.put((byte) 0);
                        encodeInteger(target, headerName.length(), 7);
                        for (int j = 0; j < headerName.length(); ++j) {
                            target.put(headerName.byteAt(j));
                        }

                        target.put((byte) 0); //to use encodeInteger we need to place the first byte in the buffer.
                        encodeInteger(target, val.length(), 7);
                        for (int j = 0; j < val.length(); ++j) {
                            target.put((byte) val.charAt(j));
                        }
                        addToDynamicTable(headerName, val);
                    } else if (tableEntry == null) {
                        //literal never indexed
                        target.put((byte) (1 << 4));
                        target.put((byte) 0); //to use encodeInteger we need to place the first byte in the buffer.
                        encodeInteger(target, headerName.length(), 7);
                        headerName.appendTo(target);

                        target.put((byte) 0); //to use encodeInteger we need to place the first byte in the buffer.
                        encodeInteger(target, val.length(), 7);
                        for (int j = 0; j < val.length(); ++j) {
                            target.put((byte) val.charAt(j));
                        }

                    } else {
                        //so we know something is already in the table
                        if (val.equals(tableEntry.value)) {
                            //the whole thing is in the table
                            target.put((byte) (1 << 7));
                            encodeInteger(target, tableEntry.getPosition(), 7);
                        } else {
                            if (canIndex) {
                                //add the entry to the dynamic table
                                target.put((byte) (1 << 6));
                                encodeInteger(target, tableEntry.getPosition(), 6);

                                target.put((byte) 0); //to use encodeInteger we need to place the first byte in the buffer.
                                encodeInteger(target, val.length(), 7);
                                for (int j = 0; j < val.length(); ++j) {
                                    target.put((byte) val.charAt(j));
                                }
                                addToDynamicTable(headerName, val);

                            } else {
                                target.put((byte) (1 << 4));
                                encodeInteger(target, tableEntry.getPosition(), 4);

                                target.put((byte) 0); //to use encodeInteger we need to place the first byte in the buffer.
                                encodeInteger(target, val.length(), 7);
                                for (int j = 0; j < val.length(); ++j) {
                                    target.put((byte) val.charAt(j));
                                }
                            }
                        }
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

    private void addToDynamicTable(HttpString headerName, String val) {
        int pos = entryPositionCounter++;
        DynamicTableEntry d = new DynamicTableEntry(headerName, val, -pos);
        currentTableSize += d.size;
        runEvictionIfRequired();
        if (entryPositionCounter == Integer.MAX_VALUE) {
            //prevent rollover
            preventPositionRollover();
        }

    }

    private void preventPositionRollover() {
        //if the position counter is about to roll over we iterate all the table entries
        //and set their position to their actual position
        for (Map.Entry<HttpString, List<TableEntry>> entry : dynamicTable.entrySet()) {
            for (TableEntry t : entry.getValue()) {
                t.position = t.getPosition();
            }
        }
        entryPositionCounter = 0;
    }

    private void runEvictionIfRequired() {

        while (currentTableSize > maxTableSize) {
            TableEntry next = evictionQueue.poll();
            if(next == null) {
                return;
            }
            currentTableSize -= next.size;
            List<TableEntry> list = dynamicTable.get(next.name);
            list.remove(next);
            if(list.isEmpty()) {
                dynamicTable.remove(next.name);
            }
        }
    }

    private TableEntry findInTable(HttpString headerName, String value) {
        TableEntry[] staticTable = ENCODING_STATIC_TABLE.get(headerName);
        if (staticTable != null) {
            for (TableEntry st : staticTable) {
                if (st.value != null && st.value.equals(value)) { //todo: some form of lookup?
                    return st;
                }
            }
        }
        List<TableEntry> dynamic = dynamicTable.get(headerName);
        if (dynamic != null) {
            for (TableEntry st : dynamic) {
                if (st.value.equals(value)) { //todo: some form of lookup?
                    return st;
                }
            }
        }
        if (staticTable != null) {
            return staticTable[0];
        }
        return null;
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

    static class TableEntry {
        final HttpString name;
        final String value;
        final int size;
        int position;

        TableEntry(HttpString name, String value, int position) {
            this.name = name;
            this.value = value;
            this.position = position;
            if (value != null) {
                this.size = 32 + name.length() + value.length();
            } else {
                this.size = -1;
            }
        }

        public int getPosition() {
            return position;
        }
    }

    class DynamicTableEntry extends TableEntry {

        DynamicTableEntry(HttpString name, String value, int position) {
            super(name, value, position);
        }

        @Override
        public int getPosition() {
            return super.getPosition() + entryPositionCounter;
        }
    }

    public interface IndexFunction {
        boolean shouldUseIndexing(HttpString header, String value);
    }
}
