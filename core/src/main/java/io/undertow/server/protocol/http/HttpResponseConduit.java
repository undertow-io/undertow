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

package io.undertow.server.protocol.http;

import io.undertow.UndertowMessages;
import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.xnio.Buffers;
import org.xnio.IoUtils;
import io.undertow.connector.ByteBufferPool;
import io.undertow.connector.PooledByteBuffer;
import org.xnio.XnioWorker;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HttpResponseConduit extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final ByteBufferPool pool;

    private int state = STATE_START;

    private long fiCookie = -1L;
    private String string;
    private HeaderValues headerValues;
    private int valueIdx;
    private int charIndex;
    private PooledByteBuffer pooledBuffer;
    private PooledByteBuffer pooledFileTransferBuffer;
    private HttpServerExchange exchange;

    private ByteBuffer[] writevBuffer;
    private boolean done = false;

    private static final int STATE_BODY = 0; // Message body, normal pass-through operation
    private static final int STATE_START = 1; // No headers written yet
    private static final int STATE_HDR_NAME = 2; // Header name indexed by charIndex
    private static final int STATE_HDR_D = 3; // Header delimiter ':'
    private static final int STATE_HDR_DS = 4; // Header delimiter ': '
    private static final int STATE_HDR_VAL = 5; // Header value
    private static final int STATE_HDR_EOL_CR = 6; // Header line CR
    private static final int STATE_HDR_EOL_LF = 7; // Header line LF
    private static final int STATE_HDR_FINAL_CR = 8; // Final CR
    private static final int STATE_HDR_FINAL_LF = 9; // Final LF
    private static final int STATE_BUF_FLUSH = 10; // flush the buffer and go to writing body

    private static final int MASK_STATE = 0x0000000F;
    private static final int FLAG_SHUTDOWN = 0x00000010;

    HttpResponseConduit(final StreamSinkConduit next, final ByteBufferPool pool) {
        super(next);
        this.pool = pool;
    }

    HttpResponseConduit(final StreamSinkConduit next, final ByteBufferPool pool, HttpServerExchange exchange) {
        super(next);
        this.pool = pool;
        this.exchange = exchange;
    }
    void reset(HttpServerExchange exchange) {

        this.exchange = exchange;
        state = STATE_START;
        fiCookie = -1L;
        string = null;
        headerValues = null;
        valueIdx = 0;
        charIndex = 0;
    }

    /**
     * Handles writing out the header data. It can also take a byte buffer of user
     * data, to enable both user data and headers to be written out in a single operation,
     * which has a noticeable performance impact.
     * <p/>
     * It is up to the caller to note the current position of this buffer before and after they
     * call this method, and use this to figure out how many bytes (if any) have been written.
     *
     * @param state
     * @param userData
     * @return
     * @throws IOException
     */
    private int processWrite(int state, final Object userData, int pos, int length) throws IOException {
        if (done || exchange == null) {
            throw new ClosedChannelException();
        }
        try {
            assert state != STATE_BODY;
            if (state == STATE_BUF_FLUSH) {
                final ByteBuffer byteBuffer = pooledBuffer.getBuffer();
                do {
                    long res = 0;
                    ByteBuffer[] data;
                    if (userData == null || length == 0) {
                        res = next.write(byteBuffer);
                    } else if (userData instanceof ByteBuffer) {
                        data = writevBuffer;
                        if (data == null) {
                            data = writevBuffer = new ByteBuffer[2];
                        }
                        data[0] = byteBuffer;
                        data[1] = (ByteBuffer) userData;
                        res = next.write(data, 0, 2);
                    } else {
                        data = writevBuffer;
                        if (data == null || data.length < length + 1) {
                            data = writevBuffer = new ByteBuffer[length + 1];
                        }
                        data[0] = byteBuffer;
                        System.arraycopy(userData, pos, data, 1, length);
                        res = next.write(data, 0, data.length);
                    }
                    if (res == 0) {
                        return STATE_BUF_FLUSH;
                    }
                } while (byteBuffer.hasRemaining());
                bufferDone();
                return STATE_BODY;
            } else if (state != STATE_START) {
                return processStatefulWrite(state, userData, pos, length);
            }

            //merge the cookies into the header map
            Connectors.flattenCookies(exchange);

            if (pooledBuffer == null) {
                pooledBuffer = pool.allocate();
            }
            ByteBuffer buffer = pooledBuffer.getBuffer();


            assert buffer.remaining() >= 50;
            exchange.getProtocol().appendTo(buffer);
            buffer.put((byte) ' ');
            int code = exchange.getStatusCode();
            assert 999 >= code && code >= 100;
            buffer.put((byte) (code / 100 + '0'));
            buffer.put((byte) (code / 10 % 10 + '0'));
            buffer.put((byte) (code % 10 + '0'));
            buffer.put((byte) ' ');

            String string = exchange.getReasonPhrase();
            if(string == null) {
                string = StatusCodes.getReason(code);
            }
            if(string.length() > buffer.remaining()) {
                pooledBuffer.close();
                pooledBuffer = null;
                truncateWrites();
                throw UndertowMessages.MESSAGES.reasonPhraseToLargeForBuffer(string);
            }
            writeString(buffer, string);
            buffer.put((byte) '\r').put((byte) '\n');

            int remaining = buffer.remaining();


            HeaderMap headers = exchange.getResponseHeaders();
            long fiCookie = headers.fastIterateNonEmpty();
            while (fiCookie != -1) {
                HeaderValues headerValues = headers.fiCurrent(fiCookie);

                HttpString header = headerValues.getHeaderName();
                int headerSize = header.length();
                int valueIdx = 0;
                while (valueIdx < headerValues.size()) {
                    remaining -= (headerSize + 2);

                    if (remaining < 0) {
                        this.fiCookie = fiCookie;
                        this.string = string;
                        this.headerValues = headerValues;
                        this.valueIdx = valueIdx;
                        this.charIndex = 0;
                        this.state = STATE_HDR_NAME;
                        buffer.flip();
                        return processStatefulWrite(STATE_HDR_NAME, userData, pos, length);
                    }
                    header.appendTo(buffer);
                    buffer.put((byte) ':').put((byte) ' ');
                    string = headerValues.get(valueIdx++);

                    remaining -= (string.length() + 2);
                    if (remaining < 2) {//we use 2 here, to make sure we always have room for the final \r\n
                        this.fiCookie = fiCookie;
                        this.string = string;
                        this.headerValues = headerValues;
                        this.valueIdx = valueIdx;
                        this.charIndex = 0;
                        this.state = STATE_HDR_VAL;
                        buffer.flip();
                        return processStatefulWrite(STATE_HDR_VAL, userData, pos, length);
                    }
                    writeString(buffer, string);
                    buffer.put((byte) '\r').put((byte) '\n');
                }
                fiCookie = headers.fiNextNonEmpty(fiCookie);
            }
            buffer.put((byte) '\r').put((byte) '\n');
            buffer.flip();
            do {
                long res = 0;
                ByteBuffer[] data;
                if (userData == null) {
                    res = next.write(buffer);
                } else if (userData instanceof ByteBuffer) {
                    data = writevBuffer;
                    if (data == null) {
                        data = writevBuffer = new ByteBuffer[2];
                    }
                    data[0] = buffer;
                    data[1] = (ByteBuffer) userData;
                    res = next.write(data, 0, 2);
                } else {
                    data = writevBuffer;
                    if (data == null || data.length < length + 1) {
                        data = writevBuffer = new ByteBuffer[length + 1];
                    }
                    data[0] = buffer;
                    System.arraycopy(userData, pos, data, 1, length);
                    res = next.write(data, 0, length + 1);
                }
                if (res == 0) {
                    return STATE_BUF_FLUSH;
                }
            } while (buffer.hasRemaining());
            bufferDone();
            return STATE_BODY;
        } catch (IOException | RuntimeException e) {
            //WFLY-4696, just to be safe
            if (pooledBuffer != null) {
                pooledBuffer.close();
                pooledBuffer = null;
            }
            throw e;
        }
    }

    private void bufferDone() {
        if(exchange == null) {
            return;
        }
        HttpServerConnection connection = (HttpServerConnection)exchange.getConnection();
        if(connection.getExtraBytes() != null && connection.isOpen() && exchange.isRequestComplete()) {
            //if we are pipelining we hold onto the buffer
            pooledBuffer.getBuffer().clear();
        } else {

            pooledBuffer.close();
            pooledBuffer = null;
            this.exchange = null;
        }
    }

    private static void writeString(ByteBuffer buffer, String string) {
        int length = string.length();
        for (int charIndex = 0; charIndex < length; charIndex++) {
            char c = string.charAt(charIndex);
            if(c != '\r' && c != '\n') {
                buffer.put((byte) c);
            } else {
                buffer.put((byte) ' ');
            }
        }
    }


    /**
     * Handles writing out the header data in the case where is is too big to fit into a buffer. This is a much slower code path.
     */
    private int processStatefulWrite(int state, final Object userData, int pos, int len) throws IOException {
        ByteBuffer buffer = pooledBuffer.getBuffer();
        long fiCookie = this.fiCookie;
        int valueIdx = this.valueIdx;
        int charIndex = this.charIndex;
        int length;
        String string = this.string;
        HeaderValues headerValues = this.headerValues;
        int res;
        // BUFFER IS FLIPPED COMING IN
        if (buffer.hasRemaining()) {
            do {
                res = next.write(buffer);
                if (res == 0) {
                    return state;
                }
            } while (buffer.hasRemaining());
        }
        buffer.clear();
        HeaderMap headers = exchange.getResponseHeaders();
        // BUFFER IS NOW EMPTY FOR FILLING
        for (; ; ) {
            switch (state) {
                case STATE_HDR_NAME: {
                    final HttpString headerName = headerValues.getHeaderName();
                    length = headerName.length();
                    while (charIndex < length) {
                        if (buffer.hasRemaining()) {
                            buffer.put(headerName.byteAt(charIndex++));
                        } else {
                            buffer.flip();
                            do {
                                res = next.write(buffer);
                                if (res == 0) {
                                    this.string = string;
                                    this.headerValues = headerValues;
                                    this.charIndex = charIndex;
                                    this.fiCookie = fiCookie;
                                    this.valueIdx = valueIdx;
                                    return STATE_HDR_NAME;
                                }
                            } while (buffer.hasRemaining());
                            buffer.clear();
                        }
                    }
                    // fall thru
                }
                case STATE_HDR_D: {
                    if (!buffer.hasRemaining()) {
                        buffer.flip();
                        do {
                            res = next.write(buffer);
                            if (res == 0) {
                                this.string = string;
                                this.headerValues = headerValues;
                                this.charIndex = charIndex;
                                this.fiCookie = fiCookie;
                                this.valueIdx = valueIdx;
                                return STATE_HDR_D;
                            }
                        } while (buffer.hasRemaining());
                        buffer.clear();
                    }
                    buffer.put((byte) ':');
                    // fall thru
                }
                case STATE_HDR_DS: {
                    if (!buffer.hasRemaining()) {
                        buffer.flip();
                        do {
                            res = next.write(buffer);
                            if (res == 0) {
                                this.string = string;
                                this.headerValues = headerValues;
                                this.charIndex = charIndex;
                                this.fiCookie = fiCookie;
                                this.valueIdx = valueIdx;
                                return STATE_HDR_DS;
                            }
                        } while (buffer.hasRemaining());
                        buffer.clear();
                    }
                    buffer.put((byte) ' ');
                    //if (valueIterator == null) {
                    //    valueIterator = exchange.getResponseHeaders().get(headerName).iterator();
                    //}
                    string = headerValues.get(valueIdx++);
                    charIndex = 0;
                    // fall thru
                }
                case STATE_HDR_VAL: {
                    length = string.length();
                    while (charIndex < length) {
                        if (buffer.hasRemaining()) {
                            buffer.put((byte) string.charAt(charIndex++));
                        } else {
                            buffer.flip();
                            do {
                                res = next.write(buffer);
                                if (res == 0) {
                                    this.string = string;
                                    this.headerValues = headerValues;
                                    this.charIndex = charIndex;
                                    this.fiCookie = fiCookie;
                                    this.valueIdx = valueIdx;
                                    return STATE_HDR_VAL;
                                }
                            } while (buffer.hasRemaining());
                            buffer.clear();
                        }
                    }
                    charIndex = 0;
                    if (valueIdx == headerValues.size()) {
                        if (!buffer.hasRemaining()) {
                            if (flushHeaderBuffer(buffer, string, headerValues, charIndex, fiCookie, valueIdx))
                                return STATE_HDR_EOL_CR;
                        }
                        buffer.put((byte) 13); // CR
                        if (!buffer.hasRemaining()) {
                            if (flushHeaderBuffer(buffer, string, headerValues, charIndex, fiCookie, valueIdx))
                                return STATE_HDR_EOL_LF;
                        }
                        buffer.put((byte) 10); // LF
                        if ((fiCookie = headers.fiNextNonEmpty(fiCookie)) != -1L) {
                            headerValues = headers.fiCurrent(fiCookie);
                            valueIdx = 0;
                            state = STATE_HDR_NAME;
                            break;
                        } else {
                            if (!buffer.hasRemaining()) {
                                if (flushHeaderBuffer(buffer, string, headerValues, charIndex, fiCookie, valueIdx))
                                    return STATE_HDR_FINAL_CR;
                            }
                            buffer.put((byte) 13); // CR
                            if (!buffer.hasRemaining()) {
                                if (flushHeaderBuffer(buffer, string, headerValues, charIndex, fiCookie, valueIdx))
                                    return STATE_HDR_FINAL_LF;
                            }
                            buffer.put((byte) 10); // LF
                            this.fiCookie = -1;
                            this.valueIdx = 0;
                            this.string = null;
                            buffer.flip();
                            //for performance reasons we use a gather write if there is user data
                            if (userData == null) {
                                do {
                                    res = next.write(buffer);
                                    if (res == 0) {
                                        return STATE_BUF_FLUSH;
                                    }
                                } while (buffer.hasRemaining());
                            } else if(userData instanceof ByteBuffer) {
                                ByteBuffer[] b = {buffer, (ByteBuffer) userData};
                                do {
                                    long r = next.write(b, 0, b.length);
                                    if (r == 0 && buffer.hasRemaining()) {
                                        return STATE_BUF_FLUSH;
                                    }
                                } while (buffer.hasRemaining());
                            } else {
                                ByteBuffer[] b = new ByteBuffer[1 + len];
                                b[0] = buffer;
                                System.arraycopy(userData, pos, b, 1, len);
                                do {
                                    long r = next.write(b, 0, b.length);
                                    if (r == 0 && buffer.hasRemaining()) {
                                        return STATE_BUF_FLUSH;
                                    }
                                } while (buffer.hasRemaining());
                            }
                            bufferDone();
                            return STATE_BODY;
                        }
                        // not reached
                    }
                    // fall thru
                }
                // Clean-up states
                case STATE_HDR_EOL_CR: {
                    if (!buffer.hasRemaining()) {
                        if (flushHeaderBuffer(buffer, string, headerValues, charIndex, fiCookie, valueIdx))
                            return STATE_HDR_EOL_CR;
                    }
                    buffer.put((byte) 13); // CR
                }
                case STATE_HDR_EOL_LF: {
                    if (!buffer.hasRemaining()) {
                        if (flushHeaderBuffer(buffer, string, headerValues, charIndex, fiCookie, valueIdx))
                            return STATE_HDR_EOL_LF;
                    }
                    buffer.put((byte) 10); // LF
                    if (valueIdx < headerValues.size()) {
                        state = STATE_HDR_NAME;
                        break;
                    } else if ((fiCookie = headers.fiNextNonEmpty(fiCookie)) != -1L) {
                        headerValues = headers.fiCurrent(fiCookie);
                        valueIdx = 0;
                        state = STATE_HDR_NAME;
                        break;
                    }
                    // fall thru
                }
                case STATE_HDR_FINAL_CR: {
                    if (!buffer.hasRemaining()) {
                        if (flushHeaderBuffer(buffer, string, headerValues, charIndex, fiCookie, valueIdx))
                            return STATE_HDR_FINAL_CR;
                    }
                    buffer.put((byte) 13); // CR
                    // fall thru
                }
                case STATE_HDR_FINAL_LF: {
                    if (!buffer.hasRemaining()) {
                        if (flushHeaderBuffer(buffer, string, headerValues, charIndex, fiCookie, valueIdx))
                            return STATE_HDR_FINAL_LF;
                    }
                    buffer.put((byte) 10); // LF
                    this.fiCookie = -1L;
                    this.valueIdx = 0;
                    this.string = null;
                    buffer.flip();
                    //for performance reasons we use a gather write if there is user data
                    if (userData == null) {
                        do {
                            res = next.write(buffer);
                            if (res == 0) {
                                return STATE_BUF_FLUSH;
                            }
                        } while (buffer.hasRemaining());
                    } else if(userData instanceof ByteBuffer) {
                        ByteBuffer[] b = {buffer, (ByteBuffer) userData};
                        do {
                            long r = next.write(b, 0, b.length);
                            if (r == 0 && buffer.hasRemaining()) {
                                return STATE_BUF_FLUSH;
                            }
                        } while (buffer.hasRemaining());
                    } else {
                        ByteBuffer[] b = new ByteBuffer[1 + len];
                        b[0] = buffer;
                        System.arraycopy(userData, pos, b, 1, len);
                        do {
                            long r = next.write(b, 0, b.length);
                            if (r == 0 && buffer.hasRemaining()) {
                                return STATE_BUF_FLUSH;
                            }
                        } while (buffer.hasRemaining());
                    }
                    // fall thru
                }
                case STATE_BUF_FLUSH: {
                    // buffer was successfully flushed above
                    bufferDone();
                    return STATE_BODY;
                }
                default: {
                    throw new IllegalStateException();
                }
            }
        }
    }

    private boolean flushHeaderBuffer(ByteBuffer buffer, String string, HeaderValues headerValues, int charIndex, long fiCookie, int valueIdx) throws IOException {
        int res;
        buffer.flip();
        do {
            res = next.write(buffer);
            if (res == 0) {
                this.string = string;
                this.headerValues = headerValues;
                this.charIndex = charIndex;
                this.fiCookie = fiCookie;
                this.valueIdx = valueIdx;
                return true;
            }
        } while (buffer.hasRemaining());
        buffer.clear();
        return false;
    }

    public int write(final ByteBuffer src) throws IOException {
        try {
            int oldState = this.state;
            int state = oldState & MASK_STATE;
            int alreadyWritten = 0;
            int originalRemaining = -1;
            try {
                if (state != 0) {
                    originalRemaining = src.remaining();
                    state = processWrite(state, src, -1, -1);
                    if (state != 0) {
                        return 0;
                    }
                    alreadyWritten = originalRemaining - src.remaining();
                    if (allAreSet(oldState, FLAG_SHUTDOWN)) {
                        next.terminateWrites();
                        throw new ClosedChannelException();
                    }
                }
                if (alreadyWritten != originalRemaining) {
                    return next.write(src) + alreadyWritten;
                }
                return alreadyWritten;
            } finally {
                this.state = oldState & ~MASK_STATE | state;
            }
        } catch(IOException|RuntimeException e) {
            if(exchange != null) {
                IoUtils.safeClose(exchange.getConnection());
            }
            throw e;
        }
    }

    public long write(final ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

    public long write(final ByteBuffer[] srcs, final int offset, final int length) throws IOException {
        if (length == 0) {
            return 0L;
        }
        int oldVal = state;
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                long rem = Buffers.remaining(srcs, offset, length);
                state = processWrite(state, srcs, offset, length);

                long ret  = rem - Buffers.remaining(srcs, offset, length);
                if (state != 0) {
                    return ret;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    next.terminateWrites();
                    throw new ClosedChannelException();
                }
                //we don't attempt to write again
                return ret;
            }
            return length == 1 ? next.write(srcs[offset]) : next.write(srcs, offset, length);
        } catch (IOException | RuntimeException e) {
            if(exchange != null) {
                IoUtils.safeClose(exchange.getConnection());
            }
            throw e;
        } finally {
            this.state = oldVal & ~MASK_STATE | state;
        }
    }

    public long transferFrom(final FileChannel src, final long position, final long count) throws IOException {
        try {
            if (state != 0) {
                if(pooledFileTransferBuffer != null) {
                    try {
                        return write(pooledFileTransferBuffer.getBuffer());
                    } catch (IOException|RuntimeException e) {
                        if(pooledFileTransferBuffer != null) {
                            pooledFileTransferBuffer.close();
                            pooledFileTransferBuffer = null;
                        }
                        throw e;
                    } finally {
                        if(pooledFileTransferBuffer != null) {
                            if (!pooledFileTransferBuffer.getBuffer().hasRemaining()) {
                                pooledFileTransferBuffer.close();
                                pooledFileTransferBuffer = null;
                            }
                        }
                    }
                } else {
                    final PooledByteBuffer pooled = exchange.getConnection().getByteBufferPool().allocate();

                    ByteBuffer buffer = pooled.getBuffer();
                    try {
                        int res = src.read(buffer);
                        buffer.flip();
                        if (res <= 0) {
                            return res;
                        }
                        return write(buffer);
                    } finally {
                        if(buffer.hasRemaining()) {
                            pooledFileTransferBuffer = pooled;
                        } else {
                            pooled.close();
                        }
                    }
                }
            } else {
                return next.transferFrom(src, position, count);
            }
        } catch (IOException | RuntimeException e) {
            if(exchange != null) {
                IoUtils.safeClose(exchange.getConnection());
            }
            throw e;
        }
    }

    public long transferFrom(final StreamSourceChannel source, final long count, final ByteBuffer throughBuffer) throws IOException {
        if (state != 0) {
            return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
        } else {
            return next.transferFrom(source, count, throughBuffer);
        }
    }

    @Override
    public int writeFinal(ByteBuffer src) throws IOException {
        try {
            return Conduits.writeFinalBasic(this, src);
        } catch (IOException | RuntimeException e) {
            if(exchange != null) {
                IoUtils.safeClose(exchange.getConnection());
            }
            throw e;
        }
    }

    @Override
    public long writeFinal(ByteBuffer[] srcs, int offset, int length) throws IOException {
        try {
            return Conduits.writeFinalBasic(this, srcs, offset, length);
        } catch (IOException | RuntimeException e) {
            if(exchange != null) {
                IoUtils.safeClose(exchange.getConnection());
            }
            throw e;
        }
    }

    public boolean flush() throws IOException {
        int oldVal = state;
        int state = oldVal & MASK_STATE;
        try {
            if (state != 0) {
                state = processWrite(state, null, -1, -1);
                if (state != 0) {
                    return false;
                }
                if (allAreSet(oldVal, FLAG_SHUTDOWN)) {
                    next.terminateWrites();
                    // fall out to the flush
                }
            }
            return next.flush();
        } catch (IOException | RuntimeException e) {
            if(exchange != null) {
                IoUtils.safeClose(exchange.getConnection());
            }
            throw e;
        } finally {
            this.state = oldVal & ~MASK_STATE | state;
        }
    }


    public void terminateWrites() throws IOException {
        try {
            int oldVal = this.state;
            if (allAreClear(oldVal, MASK_STATE)) {
                next.terminateWrites();
                return;
            }
            this.state = oldVal | FLAG_SHUTDOWN;
        } catch (IOException | RuntimeException e) {
            if(exchange != null) {
                IoUtils.safeClose(exchange.getConnection());
            }
            throw e;
        }
    }

    public void truncateWrites() throws IOException {
        try {
            next.truncateWrites();
        } catch (IOException | RuntimeException e) {
            if(exchange != null) {
                IoUtils.safeClose(exchange.getConnection());
            }
            throw e;
        } finally {
            if (pooledBuffer != null) {
                bufferDone();
            }
            if(pooledFileTransferBuffer != null) {
                pooledFileTransferBuffer.close();
                pooledFileTransferBuffer = null;
            }
        }
    }

    public XnioWorker getWorker() {
        return next.getWorker();
    }

    void freeBuffers() {
        done = true;
        if(pooledBuffer != null) {
            bufferDone();
        }
        if(pooledFileTransferBuffer != null) {
            pooledFileTransferBuffer.close();
            pooledFileTransferBuffer = null;
        }
    }
}
