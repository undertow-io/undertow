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

package io.undertow.conduits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;

import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.PushBackStreamChannel;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.conduits.AbstractStreamSourceConduit;
import org.xnio.conduits.ConduitReadableByteChannel;
import org.xnio.conduits.StreamSourceConduit;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;
import static org.xnio.Bits.anyAreSet;
import static org.xnio.Bits.longBitMask;

/**
 * Channel to de-chunkify data
 *
 * @author Stuart Douglas
 */
public class ChunkedStreamSourceConduit extends AbstractStreamSourceConduit<StreamSourceConduit> {

    private final BufferWrapper bufferWrapper;
    private final ConduitListener<? super ChunkedStreamSourceConduit> finishListener;

    private long state;

    private final long maxSize;
    private long remainingAllowed;

    private static final long FLAG_READ_ENTERED = 1L << 63L;
    private static final long FLAG_CLOSED = 1L << 62L;
    private static final long FLAG_SUS_RES_SHUT = 1L << 61L;
    private static final long FLAG_FINISHED = 1L << 60L;
    private static final long FLAG_READING_LENGTH = 1L << 59L;
    private static final long FLAG_READING_TILL_END_OF_LINE = 1L << 58L;
    private static final long FLAG_READING_NEWLINE = 1L << 57L;
    private static final long MASK_COUNT = longBitMask(0, 56);

    public ChunkedStreamSourceConduit(final StreamSourceConduit next, final PushBackStreamChannel channel, final Pool<ByteBuffer> pool, final ConduitListener<? super ChunkedStreamSourceConduit> finishListener, final long maxLength) {
        this(next, new BufferWrapper() {
            @Override
            public Pooled<ByteBuffer> allocate() {
                return pool.allocate();
            }

            @Override
            public void pushBack(Pooled<ByteBuffer> pooled) {
                channel.unget(pooled);
            }
        }, finishListener, maxLength);
    }

    public ChunkedStreamSourceConduit(final StreamSourceConduit next, final HttpServerExchange exchange, final ConduitListener<? super ChunkedStreamSourceConduit> finishListener, final long maxLength) {
        this(next, new BufferWrapper() {
            @Override
            public Pooled<ByteBuffer> allocate() {
                return exchange.getConnection().getBufferPool().allocate();
            }

            @Override
            public void pushBack(Pooled<ByteBuffer> pooled) {
                exchange.getConnection().setExtraBytes(pooled);
            }
        }, finishListener, maxLength);
    }

    protected ChunkedStreamSourceConduit(final StreamSourceConduit next, final BufferWrapper bufferWrapper, final ConduitListener<? super ChunkedStreamSourceConduit> finishListener, final long maxLength) {
        super(next);
        this.bufferWrapper = bufferWrapper;
        this.finishListener = finishListener;
        this.remainingAllowed = maxLength;
        this.maxSize = maxLength;
        state = FLAG_READING_LENGTH;
    }

    public long transferTo(final long position, final long count, final FileChannel target) throws IOException {
        return target.transferFrom(new ConduitReadableByteChannel(this), position, count);
    }

    private void updateRemainingAllowed(final int written) throws IOException {
        remainingAllowed -= written;
        if (remainingAllowed < 0) {
            throw UndertowMessages.MESSAGES.requestEntityWasTooLarge(maxSize);
        }
    }

    private void checkMaxLength() throws IOException {
        if (remainingAllowed < 0) {
            throw UndertowMessages.MESSAGES.requestEntityWasTooLarge(maxSize);
        }
    }

    public long transferTo(final long count, final ByteBuffer throughBuffer, final StreamSinkChannel target) throws IOException {
        return IoUtils.transfer(new ConduitReadableByteChannel(this), count, throughBuffer, target);
    }

    public long read(final ByteBuffer[] dsts, final int offset, final int length) throws IOException {
        for (int i = offset; i < length; ++i) {
            if (dsts[i].hasRemaining()) {
                return read(dsts[i]);
            }
        }
        return 0;
    }

    @Override
    public void terminateReads() throws IOException {
        if(!isFinished()) {
            super.terminateReads();
            throw UndertowMessages.MESSAGES.chunkedChannelClosedMidChunk();
        }
    }

    public int read(final ByteBuffer dst) throws IOException {
        checkMaxLength();
        final long oldVal = state;
        //we have read the last chunk, we just return EOF
        if (anyAreSet(oldVal, FLAG_FINISHED)) {
            return -1;
        }
        if (anyAreSet(oldVal, FLAG_CLOSED)) {
            throw new ClosedChannelException();
        }

        long chunkRemaining = oldVal & MASK_COUNT;
        Pooled<ByteBuffer> pooled = bufferWrapper.allocate();
        ByteBuffer buf = pooled.getResource();
        int r = next.read(buf);
        buf.flip();
        if (r == -1) {
            //Channel is broken, not sure how best to report it
            throw new ClosedChannelException();
        } else if (r == 0) {
            return 0;
        }

        long newVal = oldVal;
        try {
            //if we are done reading
            if (allAreClear(oldVal, FLAG_READING_NEWLINE | FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE) && chunkRemaining == 0) {
                state |= FLAG_FINISHED;
                return -1;
            }

            if (chunkRemaining == 0) {
                while (anyAreSet(newVal, FLAG_READING_NEWLINE)) {
                    while (buf.hasRemaining()) {
                        byte b = buf.get();
                        if (b == '\n') {
                            newVal = newVal & ~FLAG_READING_NEWLINE | FLAG_READING_LENGTH;
                            break;
                        }
                    }
                    if (anyAreSet(newVal, FLAG_READING_NEWLINE)) {
                        buf.clear();
                        int c = next.read(buf);
                        buf.flip();
                        if (c == -1) {
                            //Channel is broken, not sure how best to report it
                            throw new ClosedChannelException();
                        } else if (c == 0) {
                            return 0;
                        }
                    }
                }

                while (anyAreSet(newVal, FLAG_READING_LENGTH)) {
                    while (buf.hasRemaining()) {
                        byte b = buf.get();
                        if ((b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b < 'F')) {
                            chunkRemaining <<= 4; //shift it 4 bytes and then add the next value to the end
                            chunkRemaining += Integer.parseInt("" + (char) b, 16);
                        } else {
                            newVal = newVal & ~FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE;
                            break;
                        }
                    }
                    if (anyAreSet(newVal, FLAG_READING_LENGTH)) {
                        buf.clear();
                        int c = next.read(buf);
                        buf.flip();
                        if (c == -1) {
                            //Channel is broken, not sure how best to report it
                            throw new ClosedChannelException();
                        } else if (c == 0) {
                            return 0;
                        }
                    }
                }
                while (anyAreSet(newVal, FLAG_READING_TILL_END_OF_LINE)) {
                    while (buf.hasRemaining()) {
                        if (buf.get() == '\n') {
                            newVal = newVal & ~FLAG_READING_TILL_END_OF_LINE;
                            break;
                        }
                    }
                    if (anyAreSet(newVal, FLAG_READING_TILL_END_OF_LINE)) {
                        buf.clear();
                        int c = next.read(buf);
                        buf.flip();
                        if (c == -1) {
                            //Channel is broken, not sure how best to report it
                            throw new ClosedChannelException();
                        } else if (c == 0) {
                            return 0;
                        }
                    }
                }

                //we have our chunk size, check to make sure it was not the last chunk
                if (allAreClear(newVal, FLAG_READING_NEWLINE | FLAG_READING_LENGTH | FLAG_READING_TILL_END_OF_LINE) && chunkRemaining == 0) {
                    newVal |= FLAG_FINISHED;
                    return -1;
                }
            }

            final int originalLimit = dst.limit();
            try {
                //now we may have some stuff in the raw buffer
                //or the raw buffer may be exhausted, and we should read directly into the destination buffer
                //from the next

                int read = 0;
                long chunkInBuffer = Math.min(buf.remaining(), chunkRemaining);
                int remaining = dst.remaining();
                if (chunkInBuffer > remaining) {
                    //it won't fit
                    int orig = buf.limit();
                    buf.limit(buf.position() + remaining);
                    dst.put(buf);
                    buf.limit(orig);
                    chunkRemaining -= remaining;
                    updateRemainingAllowed(remaining);
                    return remaining;
                } else if (buf.hasRemaining()) {
                    int old = buf.limit();
                    buf.limit((int) Math.min(old, buf.position() + chunkInBuffer));
                    try {
                        dst.put(buf);
                    } finally {
                        buf.limit(old);
                    }
                    read += chunkInBuffer;
                    chunkRemaining -= chunkInBuffer;
                }
                //there is still more to read
                //we attempt to just read it directly into the destination buffer
                //adjusting the limit as nessesary to make sure we do not read too much
                if (chunkRemaining > 0) {
                    int old = dst.limit();
                    try {
                        if (chunkRemaining < dst.remaining()) {
                            dst.limit((int) (dst.position() + chunkRemaining));
                        }
                        int c = 0;
                        do {
                            c = next.read(dst);
                            if (c > 0) {
                                read += c;
                                chunkRemaining -= c;
                            }
                        } while (c > 0 && chunkRemaining > 0);
                        if (c == -1) {
                            newVal |= FLAG_FINISHED;
                        }
                    } finally {
                        dst.limit(old);
                    }
                }

                if (chunkRemaining == 0) {
                    newVal |= FLAG_READING_NEWLINE;
                }
                updateRemainingAllowed(read);
                return read;

            } finally {
                //buffer will be freed if not needed in exitRead
                dst.limit(originalLimit);
            }

        } finally {
            newVal = (newVal & ~MASK_COUNT) | chunkRemaining;
            state = newVal;
            if (buf.hasRemaining()) {
                bufferWrapper.pushBack(pooled);
            }
            if (allAreClear(oldVal, FLAG_FINISHED) && allAreSet(newVal, FLAG_FINISHED)) {
                callFinish();
            }
        }

    }

    public boolean isFinished() {
        return anyAreSet(state, FLAG_FINISHED);
    }

    private void callFinish() {
        finishListener.handleEvent(this);
    }

    interface BufferWrapper {

        Pooled<ByteBuffer> allocate();
        void pushBack(Pooled<ByteBuffer> pooled);

    }

}
