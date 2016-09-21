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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.undertow.protocols.http2.Hpack.HeaderField;
import static io.undertow.protocols.http2.Hpack.STATIC_TABLE;
import static io.undertow.protocols.http2.Hpack.STATIC_TABLE_LENGTH;
import static io.undertow.protocols.http2.Hpack.encodeInteger;

/**
 * Encoder for HPACK frames.
 *
 * @author Stuart Douglas
 */
public class HpackEncoder {

    public static final HpackHeaderFunction DEFAULT_HEADER_FUNCTION = new HpackHeaderFunction() {
        @Override
        public boolean shouldUseIndexing(HttpString headerName, String value) {
            //content length and date change all the time
            //no need to index them, or they will churn the table
            return !headerName.equals(Headers.CONTENT_LENGTH) && !headerName.equals(Headers.DATE);
        }

        @Override
        public boolean shouldUseHuffman(HttpString header, String value) {
            return value.length() > 10; //TODO: figure out a good value for this
        }

        @Override
        public boolean shouldUseHuffman(HttpString header) {
            return header.length() > 10; //TODO: figure out a good value for this
        }


    };

    private long headersIterator = -1;
    private boolean firstPass = true;

    private HeaderMap currentHeaders;

    private int entryPositionCounter;

    private int newMaxHeaderSize = -1; //if the max header size has been changed
    private int minNewMaxHeaderSize = -1; //records the smallest value of newMaxHeaderSize, as per section 4.1

    private static final Map<HttpString, TableEntry[]> ENCODING_STATIC_TABLE;

    private final Deque<TableEntry> evictionQueue = new ArrayDeque<>();
    private final Map<HttpString, List<TableEntry>> dynamicTable = new HashMap<>();

    private byte[] overflowData;
    private int overflowPos;
    private int overflowLength;

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

    private final HpackHeaderFunction hpackHeaderFunction;

    public HpackEncoder(int maxTableSize, HpackHeaderFunction headerFunction) {
        this.maxTableSize = maxTableSize;
        this.hpackHeaderFunction = headerFunction;
    }

    public HpackEncoder(int maxTableSize) {
        this(maxTableSize, DEFAULT_HEADER_FUNCTION);
    }

    /**
     * Encodes the headers into a buffer.
     *
     * @param headers
     * @param target
     */
    public State encode(HeaderMap headers, ByteBuffer target) {
        if(overflowData != null) {
            for(int i = overflowPos; i < overflowLength; ++i) {
                if(!target.hasRemaining()) {
                    overflowPos = i;
                    return State.OVERFLOW;
                }
                target.put(overflowData[i]);
            }
            overflowData = null;
        }

        long it = headersIterator;
        if (headersIterator == -1) {
            handleTableSizeChange(target);
            //new headers map
            it = headers.fastIterate();
            currentHeaders = headers;
        } else {
            if (headers != currentHeaders) {
                throw new IllegalStateException();
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
                for (int i = 0; i < values.size(); ++i) {

                    HttpString headerName = values.getHeaderName();
                    int required = 11 + headerName.length(); //we use 11 to make sure we have enough room for the variable length itegers

                    String val = values.get(i);
                    for(int v = 0; v < val.length(); ++v) {
                        char c = val.charAt(v);
                        if(c == '\r' || c == '\n') {
                            val = val.replace('\r', ' ').replace('\n', ' ');
                            break;
                        }
                    }
                    TableEntry tableEntry = findInTable(headerName, val);

                    required += (1 + val.length());
                    boolean overflowing = false;

                    ByteBuffer current = target;
                    if (current.remaining() < required) {
                        overflowing = true;
                        current = ByteBuffer.wrap(overflowData = new byte[required]);
                        overflowPos = 0;
                    }
                    boolean canIndex = hpackHeaderFunction.shouldUseIndexing(headerName, val) && (headerName.length() + val.length() + 32) < maxTableSize; //only index if it will fit
                    if (tableEntry == null && canIndex) {
                        //add the entry to the dynamic table
                        current.put((byte) (1 << 6));
                        writeHuffmanEncodableName(current, headerName);
                        writeHuffmanEncodableValue(current, headerName, val);
                        addToDynamicTable(headerName, val);
                    } else if (tableEntry == null) {
                        //literal never indexed
                        current.put((byte) (1 << 4));
                        writeHuffmanEncodableName(current, headerName);
                        writeHuffmanEncodableValue(current, headerName, val);
                    } else {
                        //so we know something is already in the table
                        if (val.equals(tableEntry.value)) {
                            //the whole thing is in the table
                            current.put((byte) (1 << 7));
                            encodeInteger(current, tableEntry.getPosition(), 7);
                        } else {
                            if (canIndex) {
                                //add the entry to the dynamic table
                                current.put((byte) (1 << 6));
                                encodeInteger(current, tableEntry.getPosition(), 6);
                                writeHuffmanEncodableValue(current, headerName, val);
                                addToDynamicTable(headerName, val);

                            } else {
                                current.put((byte) (1 << 4));
                                encodeInteger(current, tableEntry.getPosition(), 4);
                                writeHuffmanEncodableValue(current, headerName, val);
                            }
                        }
                    }
                    if(overflowing) {
                        it = headers.fiNext(it);
                        this.headersIterator = it;
                        this.overflowLength = current.position();
                        return State.OVERFLOW;
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

    private void writeHuffmanEncodableName(ByteBuffer target, HttpString headerName) {
        if (hpackHeaderFunction.shouldUseHuffman(headerName)) {
            if(HPackHuffman.encode(target, headerName.toString(), true)) {
                return;
            }
        }
        target.put((byte) 0); //to use encodeInteger we need to place the first byte in the buffer.
        encodeInteger(target, headerName.length(), 7);
        for (int j = 0; j < headerName.length(); ++j) {
            target.put(Hpack.toLower(headerName.byteAt(j)));
        }

    }

    private void writeHuffmanEncodableValue(ByteBuffer target, HttpString headerName, String val) {
        if (hpackHeaderFunction.shouldUseHuffman(headerName, val)) {
            if (!HPackHuffman.encode(target, val, false)) {
                writeValueString(target, val);
            }
        } else {
            writeValueString(target, val);
        }
    }

    private void writeValueString(ByteBuffer target, String val) {
        target.put((byte) 0); //to use encodeInteger we need to place the first byte in the buffer.
        encodeInteger(target, val.length(), 7);
        for (int j = 0; j < val.length(); ++j) {
            target.put((byte) val.charAt(j));
        }
    }

    private void addToDynamicTable(HttpString headerName, String val) {
        int pos = entryPositionCounter++;
        DynamicTableEntry d = new DynamicTableEntry(headerName, val, -pos);
        List<TableEntry> existing = dynamicTable.get(headerName);
        if (existing == null) {
            dynamicTable.put(headerName, existing = new ArrayList<>(1));
        }
        existing.add(d);
        evictionQueue.add(d);
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
            if (next == null) {
                return;
            }
            currentTableSize -= next.size;
            List<TableEntry> list = dynamicTable.get(next.name);
            list.remove(next);
            if (list.isEmpty()) {
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
            for (int i = 0; i < dynamic.size(); ++i) {
                TableEntry st = dynamic.get(i);
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

    public void setMaxTableSize(int newSize) {
        this.newMaxHeaderSize = newSize;
        if (minNewMaxHeaderSize == -1) {
            minNewMaxHeaderSize = newSize;
        } else {
            minNewMaxHeaderSize = Math.min(newSize, minNewMaxHeaderSize);
        }
    }

    private void handleTableSizeChange(ByteBuffer target) {
        if (newMaxHeaderSize == -1) {
            return;
        }
        if (minNewMaxHeaderSize != newMaxHeaderSize) {
            target.put((byte) (1 << 5));
            encodeInteger(target, minNewMaxHeaderSize, 5);
        }
        target.put((byte) (1 << 5));
        encodeInteger(target, newMaxHeaderSize, 5);
        maxTableSize = newMaxHeaderSize;
        runEvictionIfRequired();
        newMaxHeaderSize = -1;
        minNewMaxHeaderSize = -1;
    }

    public enum State {
        COMPLETE,
        OVERFLOW,
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
            return super.getPosition() + entryPositionCounter + STATIC_TABLE_LENGTH;
        }
    }

    public interface HpackHeaderFunction {
        boolean shouldUseIndexing(HttpString header, String value);

        /**
         * Returns true if huffman encoding should be used on the header value
         *
         * @param header The header name
         * @param value  The header value to be encoded
         * @return <code>true</code> if the value should be encoded
         */
        boolean shouldUseHuffman(HttpString header, String value);

        /**
         * Returns true if huffman encoding should be used on the header name
         *
         * @param header The header name to be encoded
         * @return <code>true</code> if the value should be encoded
         */
        boolean shouldUseHuffman(HttpString header);
    }
}
