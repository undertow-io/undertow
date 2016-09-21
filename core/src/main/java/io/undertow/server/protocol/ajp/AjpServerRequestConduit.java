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

package io.undertow.server.protocol.ajp;

import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.longBitMask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;

import io.undertow.UndertowMessages;
import io.undertow.conduits.ConduitListener;
import io.undertow.server.HttpServerExchange;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.StreamSourceConduit;

/**
 * Underlying AJP request channel.
 *
 * @author Stuart Douglas
 */
public class AjpServerRequestConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private static final ByteBuffer READ_BODY_CHUNK;

    static {
        ByteBuffer readBody = ByteBuffer.allocateDirect(7);
        readBody.put((byte) 'A');
        readBody.put((byte) 'B');
        readBody.put((byte) 0);
        readBody.put((byte) 3);
        readBody.put((byte) 6);
        readBody.put((byte) 0x1F);
        readBody.put((byte) 0xFA);
        readBody.flip();
        READ_BODY_CHUNK = readBody;

    }

    private static final int HEADER_LENGTH = 6;

    /**
     * There is a packet coming from apache.
     */
    private static final long STATE_READING = 1L << 63L;
    /**
     * There is no packet coming, as we need to set a GET_BODY_CHUNK message
     */
    private static final long STATE_SEND_REQUIRED = 1L << 62L;
    /**
     * read is done
     */
    private static final long STATE_FINISHED = 1L << 61L;

    /**
     * The remaining bits are used to store the remaining chunk size.
     */
    private static final long STATE_MASK = longBitMask(0, 60);


    private final HttpServerExchange exchange;

    private final AjpServerResponseConduit ajpResponseConduit;

    /**
     * byte buffer that is used to hold header data
     */
    private final ByteBuffer headerBuffer = ByteBuffer.allocateDirect(HEADER_LENGTH);

    private final ConduitListener<? super AjpServerRequestConduit> finishListener;

    /**
    /**
     * The total amount of remaining data. If this is unknown it is -1.
     */
    private long remaining;

    /**
     * State flags, with the chunk remaining stored in the low bytes
     */
    private long state;

    /**
     * The total amount of data that has been read
     */
    private long totalRead;

    public AjpServerRequestConduit(final StreamSourceConduit delegate, HttpServerExchange exchange, AjpServerResponseConduit ajpResponseConduit, Long size, ConduitListener<? super AjpServerRequestConduit> finishListener) {
        super(delegate);
        this.exchange = exchange;
        this.ajpResponseConduit = ajpResponseConduit;
        this.finishListener = finishListener;
        if (size == null) {
            state = STATE_SEND_REQUIRED;
            remaining = -1;
        } else if (size.longValue() == 0L) {
            state = STATE_FINISHED;
            remaining = 0;
        } else {
            state = STATE_READING;
            remaining = size;
        }
    }

    @Override
    public long transferTo(long position, long count, FileChannel target) throws IOException {
        try {
            return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
        } catch (IOException | RuntimeException e) {
            IoUtils.safeClose(exchange.getConnection());
            throw e;
        }
    }

    @Override
    public long transferTo(long count, ByteBuffer throughBuffer, StreamSinkChannel target) throws IOException {
        try {
            return IoUtils.transfer(new ConduitReadableByteChannel(this), count, throughBuffer, target);
        } catch (IOException | RuntimeException e) {
            IoUtils.safeClose(exchange.getConnection());
            throw e;
        }
    }

    @Override
    public void terminateReads() throws IOException {
        if(exchange.isPersistent() && anyAreSet(state, STATE_FINISHED)) {
            return;
        }
        super.terminateReads();
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        try {
            long total = 0;
            for (int i = offset; i < length; ++i) {
                while (dsts[i].hasRemaining()) {
                    int r = read(dsts[i]);
                    if (r <= 0 && total > 0) {
                        return total;
                    } else if (r <= 0) {
                        return r;
                    } else {
                        total += r;
                    }
                }
            }
            return total;
        } catch (IOException | RuntimeException e) {
            IoUtils.safeClose(exchange.getConnection());
            throw e;
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        try {
            long state = this.state;
            if (anyAreSet(state, STATE_FINISHED)) {
                return -1;
            } else if (anyAreSet(state, STATE_SEND_REQUIRED)) {
                state = this.state = (state & STATE_MASK) | STATE_READING;
                if (ajpResponseConduit.isWriteShutdown()) {
                    this.state = STATE_FINISHED;
                    if (finishListener != null) {
                        finishListener.handleEvent(this);
                    }
                    return -1;
                }
                if (!ajpResponseConduit.doGetRequestBodyChunk(READ_BODY_CHUNK.duplicate(), this)) {
                    return 0;
                }
            }
            //we might have gone into state_reading above
            if (anyAreSet(state, STATE_READING)) {
                return doRead(dst, state);
            }
            assert STATE_FINISHED == state;
            return -1;
        } catch (IOException | RuntimeException e) {
            IoUtils.safeClose(exchange.getConnection());
            throw e;
        }
    }

    private int doRead(final ByteBuffer dst, long state) throws IOException {
        ByteBuffer headerBuffer = this.headerBuffer;
        long headerRead = HEADER_LENGTH - headerBuffer.remaining();
        long remaining = this.remaining;
        if (remaining == 0) {
            this.state = STATE_FINISHED;
            if (finishListener != null) {
                finishListener.handleEvent(this);
            }
            return -1;
        }
        long chunkRemaining;
        if (headerRead != HEADER_LENGTH) {
            int read = next.read(headerBuffer);
            if (read == -1) {
                this.state = STATE_FINISHED;
                if (finishListener != null) {
                    finishListener.handleEvent(this);
                }
                throw new ClosedChannelException();
            } else if (headerBuffer.hasRemaining()) {
                return 0;
            } else {
                headerBuffer.flip();
                byte b1 = headerBuffer.get(); //0x12
                byte b2 = headerBuffer.get(); //0x34
                if (b1 != 0x12 || b2 != 0x34) {
                    throw UndertowMessages.MESSAGES.wrongMagicNumber((b1 & 0xFF) << 8 | (b2 & 0xFF));
                }
                headerBuffer.get();//the length headers, two more than the string length header
                headerBuffer.get();
                b1 = headerBuffer.get();
                b2 = headerBuffer.get();
                chunkRemaining = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
                if (chunkRemaining == 0) {
                    this.remaining = 0;
                    this.state = STATE_FINISHED;

                    if (finishListener != null) {
                        finishListener.handleEvent(this);
                    }
                    return -1;
                }
            }
        } else {
            chunkRemaining = this.state & STATE_MASK;
        }

        int limit = dst.limit();
        try {
            if (dst.remaining() > chunkRemaining) {
                dst.limit((int) (dst.position() + chunkRemaining));
            }
            int read = next.read(dst);
            chunkRemaining -= read;
            if (remaining != -1) {
                remaining -= read;
            }
            this.totalRead += read;
            if (remaining == 0) {
                this.state = STATE_FINISHED;
                if (finishListener != null) {
                    finishListener.handleEvent(this);
                }
            } else if (chunkRemaining == 0) {
                headerBuffer.clear();
                this.state = STATE_SEND_REQUIRED;
            } else {
                this.state = (state & ~STATE_MASK) | chunkRemaining;
            }
            return read;
        } finally {
            this.remaining = remaining;
            dst.limit(limit);
            final long maxEntitySize = exchange.getMaxEntitySize();
            if (maxEntitySize > 0) {
                if (totalRead > maxEntitySize) {
                    //kill the connection, nothing else can be sent on it
                    terminateReads();
                    exchange.setPersistent(false);
                    throw UndertowMessages.MESSAGES.requestEntityWasTooLarge(maxEntitySize);
                }
            }
        }
    }

    @Override
    public void awaitReadable() throws IOException {
        try {
            if (anyAreSet(state, STATE_READING)) {
                next.awaitReadable();
            }
        } catch (IOException | RuntimeException e) {
            IoUtils.safeClose(exchange.getConnection());
            throw e;
        }
    }

    @Override
    public void awaitReadable(long time, TimeUnit timeUnit) throws IOException {
        try {
            if (anyAreSet(state, STATE_READING)) {
                next.awaitReadable(time, timeUnit);
            }
        } catch (IOException | RuntimeException e) {
            IoUtils.safeClose(exchange.getConnection());
            throw e;
        }
    }

    /**
     * Method that is called when an error occurs writing out read body chunk frames
     * @param e
     */
    void setReadBodyChunkError(IOException e) {
        IoUtils.safeClose(exchange.getConnection());
        if(isReadResumed()) {
            wakeupReads();
        }
    }
}
